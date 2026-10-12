package com.seatreserve.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * Simulated external payment gateway (e.g. Stripe, PayPal mock).
 *
 * CRITICAL ARCHITECTURAL CONSTRAINT:
 * This method MUST run outside of any pessimistic-lock database transaction boundary.
 * Specifically, it must NEVER be called from within {@code ReservationService.holdSeat()}
 * or any method holding a {@code SELECT ... FOR UPDATE} lock on the seat row.
 * Holding database locks during external network calls or artificial latency drains
 * database connection pools and causes thread starvation.
 */
@Service
@Slf4j
public class PaymentGatewayService {

    private long delayMs;
    private double failureRate;
    private final Random random = new Random();

    public PaymentGatewayService(
            @Value("${payment.gateway.delay-ms:500}") long delayMs,
            @Value("${payment.gateway.failure-rate:0.1}") double failureRate) {
        this.delayMs = delayMs;
        this.failureRate = failureRate;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    /**
     * Simulates processing an authorization and capture with an external provider.
     *
     * @param payment the payment entity containing reservation and amount details
     * @return true if payment succeeded, false if payment was declined or failed
     */
    public boolean processPayment(Payment payment) {
        log.info("Simulating payment gateway processing for paymentId={}, reservationId={}, amount={} {}",
                payment.getId(),
                payment.getReservation() != null ? payment.getReservation().getId() : null,
                payment.getAmount(),
                payment.getCurrency());

        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Payment simulation interrupted for paymentId={}", payment.getId());
                return false;
            }
        }

        // Check simulated failure rate
        boolean isFailure = random.nextDouble() < failureRate;
        if (isFailure) {
            log.warn("Payment gateway declined transaction for paymentId={}, failureRate={}",
                    payment.getId(), failureRate);
            return false;
        }

        log.info("Payment gateway approved transaction for paymentId={}", payment.getId());
        return true;
    }

    // Setter for deterministic testing
    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    public double getFailureRate() {
        return failureRate;
    }

    public long getDelayMs() {
        return delayMs;
    }
}
