package com.seatreserve.reservation;

import com.seatreserve.common.HoldExpiredException;
import com.seatreserve.common.NotRegisteredException;
import com.seatreserve.common.SeatConflictException;
import com.seatreserve.common.event.HoldExpiredEvent;
import com.seatreserve.common.event.ReservationConfirmedEvent;
import com.seatreserve.common.event.SeatHeldEvent;
import com.seatreserve.registration.RegistrationService;
import com.seatreserve.seat.Seat;
import com.seatreserve.seat.SeatRepository;
import com.seatreserve.user.User;
import com.seatreserve.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final RegistrationService registrationService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${reservation.hold-ttl-seconds}")
    private long holdTtlSeconds;

    /**
     * Attempts to place a temporary hold on a seat.
     *
     * Concurrency story (the reason this project exists):
     *   findByIdForUpdate() takes a row-level lock (SELECT ... FOR UPDATE)
     *   on the seat row. If two requests for the SAME seat arrive at the
     *   same instant, the database itself serializes them — the second
     *   transaction physically cannot read the seat row until the first
     *   transaction commits (having flipped status to HELD) or rolls back.
     *   So the second request always sees the up-to-date status and is
     *   correctly rejected with 409, instead of racing ahead on stale data.
     *
     *   The whole method is one @Transactional boundary, so the lock is
     *   held for the shortest possible time: acquired at the SELECT,
     *   released at commit right after the UPDATE + INSERT.
     */
    @Transactional
    public Reservation holdSeat(Long seatId, Long userId) {
        // Business-rule gate: attendee must be registered for the parent event before holding a seat.
        // Scalar read executed BEFORE acquiring the pessimistic lock, keeping Seat entity out of the 1st-level cache.
        Long eventId = seatRepository.findEventIdBySeatId(seatId)
                .orElseThrow(() -> new NoSuchElementException("Seat not found: " + seatId));
        if (!registrationService.isRegistered(eventId, userId)) {
            log.info("Seat hold rejected (not registered): seatId={}, userId={}, eventId={}", seatId, userId, eventId);
            throw new NotRegisteredException(
                    "User " + userId + " is not registered for event " + eventId);
        }

        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> new NoSuchElementException("Seat not found: " + seatId));

        if (seat.getStatus() != Seat.SeatStatus.AVAILABLE) {
            log.info("Seat hold rejected (conflict): seatId={}, userId={}, currentStatus={}", seatId, userId, seat.getStatus());
            throw new SeatConflictException(
                    "Seat " + seatId + " is not available (current status: " + seat.getStatus() + ")");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        seat.setStatus(Seat.SeatStatus.HELD);
        seatRepository.save(seat);

        Instant now = Instant.now();
        Reservation reservation = Reservation.builder()
                .seat(seat)
                .user(user)
                .status(Reservation.Status.PENDING)
                .idempotencyKey(generateHoldKey(seatId, userId, now))
                .heldAt(now)
                .expiresAt(now.plusSeconds(holdTtlSeconds))
                .build();

        Reservation savedReservation = reservationRepository.save(reservation);
        log.info("Seat hold succeeded: reservationId={}, seatId={}, userId={}, expiresAt={}",
                savedReservation.getId(), seatId, userId, savedReservation.getExpiresAt());

        String seatLabel = seat.getSection() + "-" + seat.getRowLabel() + "-" + seat.getSeatNumber();
        String sessionTitle = seat.getSession() != null ? seat.getSession().getTitle() : "Session";
        eventPublisher.publishEvent(new SeatHeldEvent(
                savedReservation.getId(),
                userId,
                seatId,
                seatLabel,
                sessionTitle,
                savedReservation.getHeldAt(),
                savedReservation.getExpiresAt()
        ));
        return savedReservation;
    }

    /**
     * Confirms a pending reservation. Idempotent on the client-supplied key:
     * a retried request with the same key returns the existing reservation
     * rather than double-confirming or erroring.
     * Enforces that the confirming user is the owner of the held reservation.
     */
    @Transactional
    public Reservation confirm(Long reservationId, String clientIdempotencyKey, Long userId) {
        var existing = reservationRepository.findByIdempotencyKey(clientIdempotencyKey);
        if (existing.isPresent() && existing.get().getStatus() == Reservation.Status.CONFIRMED) {
            log.info("Reservation confirmation idempotent return: reservationId={}, key={}",
                    existing.get().getId(), clientIdempotencyKey);
            return existing.get(); // safe retry — no double confirmation
        }

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("Reservation not found: " + reservationId));

        if (userId != null && reservation.getUser() != null && !reservation.getUser().getId().equals(userId)) {
            log.warn("Reservation confirmation denied: user {} attempted confirming reservation {} owned by {}",
                    userId, reservationId, reservation.getUser().getId());
            throw new com.seatreserve.common.UnauthorizedReservationException(
                    "User " + userId + " is not the owner of reservation " + reservationId);
        }

        if (reservation.getStatus() != Reservation.Status.PENDING) {
            log.info("Reservation confirmation rejected (not pending): reservationId={}, currentStatus={}",
                    reservationId, reservation.getStatus());
            throw new SeatConflictException(
                    "Reservation " + reservationId + " is not pending (status: " + reservation.getStatus() + ")");
        }

        log.info("Reservation hold expiry check: reservationId={}, seatId={}, expiresAt={}, now={}",
                reservationId, reservation.getSeat().getId(), reservation.getExpiresAt(), Instant.now());
        if (Instant.now().isAfter(reservation.getExpiresAt())) {
            log.info("Hold expired check failed: reservationId={}, seatId={}, expiredAt={}",
                    reservationId, reservation.getSeat().getId(), reservation.getExpiresAt());
            reservation.setStatus(Reservation.Status.EXPIRED);
            reservationRepository.save(reservation);
            releaseSeat(reservation.getSeat().getId());
            throw new HoldExpiredException("Hold for reservation " + reservationId + " has expired");
        }

        // Lock the seat row again for the status flip HELD -> BOOKED,
        // consistent with the same locking discipline used on hold.
        Seat seat = seatRepository.findByIdForUpdate(reservation.getSeat().getId())
                .orElseThrow(() -> new NoSuchElementException("Seat not found"));
        seat.setStatus(Seat.SeatStatus.BOOKED);
        seatRepository.save(seat);

        reservation.setStatus(Reservation.Status.CONFIRMED);
        reservation.setConfirmedAt(Instant.now());
        Reservation confirmed = reservationRepository.save(reservation);
        log.info("Reservation confirmed: reservationId={}, seatId={}, userId={}, confirmedAt={}",
                confirmed.getId(), seat.getId(), confirmed.getUser().getId(), confirmed.getConfirmedAt());

        String confirmedSeatLabel = seat.getSection() + "-" + seat.getRowLabel() + "-" + seat.getSeatNumber();
        String confirmedSessionTitle = seat.getSession() != null ? seat.getSession().getTitle() : "Session";
        eventPublisher.publishEvent(new ReservationConfirmedEvent(
                confirmed.getId(),
                confirmed.getUser().getId(),
                seat.getId(),
                confirmedSeatLabel,
                confirmedSessionTitle,
                confirmed.getConfirmedAt()
        ));
        return confirmed;
    }

    @Transactional
    public Reservation confirm(Long reservationId, String clientIdempotencyKey) {
        return confirm(reservationId, clientIdempotencyKey, null);
    }

    /**
     * Atomically marks an expired reservation as EXPIRED and releases its associated seat
     * back to AVAILABLE within a single database transaction.
     * Uses pessimistic row locking (SELECT ... FOR UPDATE) via releaseSeat().
     */
    @Transactional
    public void expireReservationAndReleaseSeat(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("Reservation not found: " + reservationId));

        if (reservation.getStatus() != Reservation.Status.PENDING) {
            return;
        }

        reservation.setStatus(Reservation.Status.EXPIRED);
        reservationRepository.save(reservation);

        // Lock the seat row and release it back to AVAILABLE in the same transaction
        releaseSeat(reservation.getSeat().getId());

        log.info("Hold expired by sweep job: reservationId={}, seatId={}, userId={}, expiredAt={}",
                reservation.getId(), reservation.getSeat().getId(),
                reservation.getUser().getId(), reservation.getExpiresAt());

        String seatLabel = reservation.getSeat() != null
                ? reservation.getSeat().getSection() + "-" + reservation.getSeat().getRowLabel() + "-" + reservation.getSeat().getSeatNumber()
                : "Seat";
        String sessionTitle = (reservation.getSeat() != null && reservation.getSeat().getSession() != null)
                ? reservation.getSeat().getSession().getTitle()
                : "Session";
        eventPublisher.publishEvent(new HoldExpiredEvent(
                reservation.getId(),
                reservation.getUser().getId(),
                reservation.getSeat().getId(),
                seatLabel,
                sessionTitle,
                Instant.now()
        ));
    }

    /** Releases a HELD seat back to AVAILABLE. Used on expiry and cancellation. */
    @Transactional
    public void releaseSeat(Long seatId) {
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> new NoSuchElementException("Seat not found: " + seatId));
        if (seat.getStatus() == Seat.SeatStatus.HELD) {
            seat.setStatus(Seat.SeatStatus.AVAILABLE);
            seatRepository.save(seat);
            log.info("Seat released back to AVAILABLE: seatId={}", seatId);
        }
    }

    private String generateHoldKey(Long seatId, Long userId, Instant now) {
        return "hold-" + seatId + "-" + userId + "-" + now.toEpochMilli();
    }
}
