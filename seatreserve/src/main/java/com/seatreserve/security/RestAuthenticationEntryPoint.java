package com.seatreserve.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatreserve.common.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/") && !uri.startsWith("/actuator/")) {
            response.sendRedirect("/login");
            return;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        ProblemDetail problem = ProblemDetail.of(
                "https://api.seatreserve.com/errors/unauthorized",
                "Authentication required",
                HttpStatus.UNAUTHORIZED.value(),
                "Full authentication is required to access this resource: " + authException.getMessage(),
                request.getRequestURI()
        );

        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
