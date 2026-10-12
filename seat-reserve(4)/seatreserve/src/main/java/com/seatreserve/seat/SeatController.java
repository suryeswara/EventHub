package com.seatreserve.seat;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.seatreserve.seat.SeatDtos.SeatResponse;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SeatController {

    private final SeatRepository seatRepository;

    /**
     * Primary endpoint: lists seats scoped to a specific session.
     * Guaranteed to have unambiguous section/row/seatNumber coordinates.
     */
    @GetMapping("/sessions/{sessionId}/seats")
    public List<SeatResponse> listSeatsForSession(@PathVariable Long sessionId) {
        return seatRepository.findBySessionId(sessionId).stream()
                .map(SeatResponse::from)
                .toList();
    }

    /**
     * Event-wide aggregate endpoint: returns seats across all sessions of the specified event.
     * Each SeatResponse explicitly contains its sessionId to disambiguate identical row/seat coordinates
     * across different sessions.
     */
    @GetMapping("/events/{eventId}/seats")
    public List<SeatResponse> listAllSeatsForEvent(@PathVariable Long eventId) {
        return seatRepository.findByEventId(eventId).stream()
                .map(SeatResponse::from)
                .toList();
    }
}
