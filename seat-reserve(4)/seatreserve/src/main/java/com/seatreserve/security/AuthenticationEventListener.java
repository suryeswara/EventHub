package com.seatreserve.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.stereotype.Component;

/**
 * Audit listener for Spring Security authentication failures.
 * Captures failed logins for auditing without logging sensitive data (passwords).
 */
@Component
@Slf4j
public class AuthenticationEventListener {

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication() != null ? String.valueOf(event.getAuthentication().getPrincipal()) : "anonymous";
        log.warn("Authentication failure - bad credentials for username: {}", username);
    }

    @EventListener
    public void onGenericFailure(AbstractAuthenticationFailureEvent event) {
        if (!(event instanceof AuthenticationFailureBadCredentialsEvent)) {
            String username = event.getAuthentication() != null ? String.valueOf(event.getAuthentication().getPrincipal()) : "anonymous";
            log.warn("Authentication failure for username: {}, reason: {}", username, event.getException().getMessage());
        }
    }
}
