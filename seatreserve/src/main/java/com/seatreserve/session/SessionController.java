package com.seatreserve.session;

import com.seatreserve.security.AppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.seatreserve.session.SessionDtos.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping("/events/{eventId}/sessions")
    public ResponseEntity<SessionResponse> createSession(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        Long organizerId = (userDetails != null) ? userDetails.getId() : null;
        SessionResponse response = sessionService.createSession(eventId, request, organizerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/sessions/{sessionId}/seats/generate")
    public ResponseEntity<GenerateSeatsResponse> generateSeats(
            @PathVariable Long sessionId,
            @Valid @RequestBody GenerateSeatsRequest request,
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        Long organizerId = (userDetails != null) ? userDetails.getId() : null;
        GenerateSeatsResponse response = sessionService.generateSeats(sessionId, request, organizerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/events/{eventId}/sessions")
    public ResponseEntity<List<SessionResponse>> listSessions(@PathVariable Long eventId) {
        List<SessionResponse> sessions = sessionService.listSessionsForEvent(eventId);
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable Long sessionId) {
        SessionResponse session = sessionService.getSession(sessionId);
        return ResponseEntity.ok(session);
    }
}
