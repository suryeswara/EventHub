package com.seatreserve.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatreserve.common.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/") && !uri.startsWith("/actuator/")) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            try {
                request.getRequestDispatcher("/error/403").forward(request, response);
            } catch (jakarta.servlet.ServletException e) {
                response.sendRedirect("/error/403");
            }
            return;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        ProblemDetail problem = ProblemDetail.of(
                "https://api.seatreserve.com/errors/forbidden",
                "Access denied",
                HttpStatus.FORBIDDEN.value(),
                "You do not have permission to access this resource: " + accessDeniedException.getMessage(),
                request.getRequestURI()
        );

        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
