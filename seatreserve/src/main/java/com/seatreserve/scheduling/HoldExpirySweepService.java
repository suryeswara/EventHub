package com.seatreserve.scheduling;

import com.seatreserve.reservation.Reservation;
import com.seatreserve.reservation.ReservationRepository;
import com.seatreserve.reservation.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Background scheduled service that periodically queries for PENDING reservations
 * whose hold TTL has expired, marks them as EXPIRED, and releases their seats back
 * to AVAILABLE using the exact pessimistic-locking releaseSeat() code path.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HoldExpirySweepService {

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${reservation.sweep-interval-ms:30000}")
    public void sweepExpiredHolds() {
        Instant now = Instant.now();
        List<Reservation> expiredHolds = reservationRepository.findByStatusAndExpiresAtBefore(
                Reservation.Status.PENDING, now);

        if (!expiredHolds.isEmpty()) {
            log.info("Hold expiry sweep started: found {} expired hold(s) to process", expiredHolds.size());
        }

        for (Reservation reservation : expiredHolds) {
            try {
                // Each expiration executes in an isolated, atomic @Transactional boundary on ReservationService
                reservationService.expireReservationAndReleaseSeat(reservation.getId());
            } catch (Exception ex) {
                log.error("Failed to expire reservation {}: {}", reservation.getId(), ex.getMessage(), ex);
            }
        }
    }
}
