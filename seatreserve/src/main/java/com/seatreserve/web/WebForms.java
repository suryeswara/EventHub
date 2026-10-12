package com.seatreserve.web;

import com.seatreserve.user.User;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WebForms {

    public record RegisterForm(
            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 6, message = "Password must be at least 6 characters")
            String password,

            @NotNull(message = "Role is required")
            User.Role role
    ) {}

    public record CreateEventForm(
            @NotBlank(message = "Event name is required")
            String name,

            @NotBlank(message = "Venue location is required")
            String venue,

            @NotNull(message = "Start time is required")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime
    ) {}

    public record CreateSessionForm(
            @NotBlank(message = "Session title is required")
            String title,

            String description,

            @NotNull(message = "Start time is required")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,

            @NotNull(message = "End time is required")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime,

            @NotNull(message = "Capacity is required")
            @Min(value = 1, message = "Capacity must be at least 1")
            Integer capacity,

            @NotNull(message = "Price is required")
            @DecimalMin(value = "0.00", message = "Price cannot be negative")
            BigDecimal price
    ) {}
}
