package com.seatreserve.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public class PaymentDtos {

    public record InitiatePaymentRequest(
            @NotNull(message = "Reservation ID is required")
            Long reservationId,

            @NotBlank(message = "Idempotency key is required")
            String idempotencyKey
    ) {}

    public record PaymentResponse(
            Long id,
            Long reservationId,
            BigDecimal amount,
            String currency,
            Payment.Status status,
            String idempotencyKey,
            Instant createdAt,
            Instant completedAt
    ) {
        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(
                    payment.getId(),
                    payment.getReservation() != null ? payment.getReservation().getId() : null,
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getStatus(),
                    payment.getIdempotencyKey(),
                    payment.getCreatedAt(),
                    payment.getCompletedAt()
            );
        }
    }
}
