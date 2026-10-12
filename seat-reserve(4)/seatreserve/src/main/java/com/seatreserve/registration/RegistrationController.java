package com.seatreserve.registration;

import com.seatreserve.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.seatreserve.registration.RegistrationDtos.RegisterRequest;
import static com.seatreserve.registration.RegistrationDtos.RegistrationResponse;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping("/{eventId}/register")
    public ResponseEntity<RegistrationResponse> register(
            @PathVariable Long eventId,
            @RequestBody(required = false) RegisterRequest request,
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        RegistrationResponse response = registrationService.registerForEvent(eventId, userDetails.getId());
        return ResponseEntity.ok(response);
    }
}
