package com.seatreserve.session;

import com.seatreserve.event.Event;
import com.seatreserve.event.EventRepository;
import com.seatreserve.seat.Seat;
import com.seatreserve.seat.SeatDtos.SeatResponse;
import com.seatreserve.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static com.seatreserve.session.SessionDtos.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public SessionResponse createSession(Long eventId, CreateSessionRequest request, Long organizerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + eventId));

        if (organizerId != null && event.getOrganizer() != null && !event.getOrganizer().getId().equals(organizerId)) {
            log.warn("Organizer authorization denied: user {} attempted modifying event {} owned by {}",
                    organizerId, eventId, event.getOrganizer().getId());
            throw new com.seatreserve.common.UnauthorizedOrganizerException(
                    "User " + organizerId + " is not the organizer of event " + eventId);
        }

        if (request.endTime().isBefore(request.startTime())) {
            throw new IllegalArgumentException("Session end time must be at or after start time");
        }

        Session session = Session.builder()
                .event(event)
                .title(request.title())
                .description(request.description())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .capacity(request.capacity())
                .price(request.price() != null ? request.price() : java.math.BigDecimal.ZERO)
                .build();

        Session saved = sessionRepository.save(session);
        log.info("Session created: sessionId={}, title={}, eventId={}, organizerId={}",
                saved.getId(), saved.getTitle(), eventId, organizerId);
        return SessionResponse.from(saved, 0L, 0L);
    }

    @Transactional
    public SessionResponse createSession(Long eventId, CreateSessionRequest request) {
        return createSession(eventId, request, null);
    }

    /**
     * Explicit seat-generation action for a session.
     * Validates that the sum of seats across all specified sections exactly matches the session's declared capacity.
     */
    @Transactional
    public GenerateSeatsResponse generateSeats(Long sessionId, GenerateSeatsRequest request, Long organizerId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));

        if (organizerId != null && session.getEvent() != null && session.getEvent().getOrganizer() != null
                && !session.getEvent().getOrganizer().getId().equals(organizerId)) {
            log.warn("Organizer authorization denied: user {} attempted generating seats for session {} on event {} owned by {}",
                    organizerId, sessionId, session.getEvent().getId(), session.getEvent().getOrganizer().getId());
            throw new com.seatreserve.common.UnauthorizedOrganizerException(
                    "User " + organizerId + " is not the organizer of event " + session.getEvent().getId());
        }

        if (seatRepository.existsBySessionId(sessionId)) {
            throw new IllegalStateException("Seats have already been generated for session " + sessionId);
        }

        int requestedSeats = request.totalSeatCount();
        if (requestedSeats != session.getCapacity()) {
            throw new IllegalArgumentException(
                    String.format("Generated seat count (%d) does not match session capacity (%d)",
                            requestedSeats, session.getCapacity()));
        }

        List<Seat> seatsToSave = new ArrayList<>(requestedSeats);
        for (SectionSpec section : request.sections()) {
            for (int row = 1; row <= section.rows(); row++) {
                String rowLabel = String.valueOf(row);
                for (int num = 1; num <= section.seatsPerRow(); num++) {
                    seatsToSave.add(Seat.builder()
                            .session(session)
                            .section(section.name())
                            .rowLabel(rowLabel)
                            .seatNumber(num)
                            .status(Seat.SeatStatus.AVAILABLE)
                            .build());
                }
            }
        }

        List<Seat> savedSeats = seatRepository.saveAll(seatsToSave);
        log.info("Seats generated: sessionId={}, seatCount={}, organizerId={}",
                sessionId, savedSeats.size(), organizerId);
        List<SeatResponse> seatResponses = savedSeats.stream().map(SeatResponse::from).toList();
        return new GenerateSeatsResponse(sessionId, savedSeats.size(), seatResponses);
    }

    @Transactional
    public GenerateSeatsResponse generateSeats(Long sessionId, GenerateSeatsRequest request) {
        return generateSeats(sessionId, request, null);
    }

    /**
     * Lists sessions for an event with available and total seat counts.
     * Solves the N+1 query problem by executing single grouped count queries.
     */
    @Transactional(readOnly = true)
    public List<SessionResponse> listSessionsForEvent(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new NoSuchElementException("Event not found: " + eventId);
        }

        // Single grouped JPQL query for available seats across all sessions of the event
        List<Object[]> availableCounts = seatRepository.countByStatusGroupedBySession(eventId, Seat.SeatStatus.AVAILABLE);
        Map<Long, Long> availableMap = availableCounts.stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        // Single grouped JPQL query for total seats across all sessions of the event
        List<Object[]> totalCounts = seatRepository.countTotalGroupedBySession(eventId);
        Map<Long, Long> totalMap = totalCounts.stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        return sessionRepository.findByEventId(eventId).stream()
                .map(session -> {
                    long total = totalMap.getOrDefault(session.getId(), 0L);
                    long available = availableMap.getOrDefault(session.getId(), 0L);
                    return SessionResponse.from(session, total, available);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));

        long total = seatRepository.countBySessionId(sessionId);
        long available = seatRepository.countBySessionIdAndStatus(sessionId, Seat.SeatStatus.AVAILABLE);
        return SessionResponse.from(session, total, available);
    }
}
