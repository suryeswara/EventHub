package com.seatreserve.reservation;

import com.seatreserve.common.PaymentDeclinedException;
import com.seatreserve.payment.Payment;
import com.seatreserve.payment.PaymentDtos;
import com.seatreserve.payment.PaymentService;
import com.seatreserve.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

import static com.seatreserve.reservation.ReservationDtos.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final PaymentService paymentService;

    @PostMapping("/events/{eventId}/seats/{seatId}/hold")
    public ResponseEntity<ReservationResponse> hold(
            @PathVariable Long eventId,
            @PathVariable Long seatId,
            @RequestBody(required = false) HoldRequest request,
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        Reservation reservation = reservationService.holdSeat(seatId, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservationResponse.from(reservation));
    }

    /**
     * Confirmation is strictly gated by payment processing.
     * ReservationService.confirm() is NOT directly called by the HTTP controller;
     * only PaymentService.initiatePayment(...) orchestrates confirmation upon gateway success.
     */
    @PostMapping("/reservations/{reservationId}/confirm")
    public ResponseEntity<ReservationResponse> confirm(
            @PathVariable Long reservationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        PaymentDtos.PaymentResponse paymentResponse = paymentService.initiatePayment(
                reservationId, userDetails.getId(), idempotencyKey);

        if (paymentResponse.status() == Payment.Status.SUCCEEDED) {
            Reservation confirmed = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new NoSuchElementException("Reservation not found: " + reservationId));
            return ResponseEntity.ok(ReservationResponse.from(confirmed));
        } else {
            throw new PaymentDeclinedException("Payment declined by gateway for reservation " + reservationId);
        }
    }

    @PostMapping("/reservations/{reservationId}/pay")
    public ResponseEntity<PaymentDtos.PaymentResponse> pay(
            @PathVariable Long reservationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        PaymentDtos.PaymentResponse paymentResponse = paymentService.initiatePayment(
                reservationId, userDetails.getId(), idempotencyKey);
        HttpStatus status = paymentResponse.status() == Payment.Status.SUCCEEDED
                ? HttpStatus.OK
                : HttpStatus.PAYMENT_REQUIRED;
        return ResponseEntity.status(status).body(paymentResponse);
    }
}
