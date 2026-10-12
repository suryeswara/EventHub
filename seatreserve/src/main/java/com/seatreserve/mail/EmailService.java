package com.seatreserve.mail;

import com.seatreserve.common.event.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final TemplateEngine templateEngine;

    /**
     * Listens to PaymentSucceededEvent asynchronously AFTER_COMMIT.
     * Generates ticket confirmation HTML email.
     * In dev mode: writes HTML to target/generated-emails/ and logs.
     * Any failure in email processing is logged and NEVER affects payment success.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        processEmail(event);
    }

    public void processEmail(PaymentSucceededEvent event) {
        log.info("EmailService received PaymentSucceededEvent for paymentId={}, reservationId={}, recipient={}",
                event.paymentId(), event.reservationId(), event.userEmail());
        try {
            String htmlContent = generateTicketConfirmationHtml(event);
            persistDevEmail(event.reservationId(), htmlContent);
            log.info("Ticket confirmation email generated successfully for reservationId={}, emailPreviewLength={}",
                    event.reservationId(), htmlContent.length());
        } catch (Exception ex) {
            // Explicit non-goal: Failed email send must NEVER roll back payment
            log.error("Failed to generate/send ticket confirmation email for reservationId={}: {}",
                    event.reservationId(), ex.getMessage(), ex);
        }
    }

    public String generateTicketConfirmationHtml(PaymentSucceededEvent event) {
        Context context = new Context();
        context.setVariable("eventName", event.eventName() != null ? event.eventName() : "Conference Event");
        context.setVariable("sessionTitle", event.sessionTitle() != null ? event.sessionTitle() : "Session");
        context.setVariable("seatLabel", event.seatLabel() != null ? event.seatLabel() : "General Admission");
        context.setVariable("reservationId", event.reservationId());
        context.setVariable("paymentId", event.paymentId());
        context.setVariable("amount", event.amount());
        context.setVariable("currency", event.currency());
        context.setVariable("completedAt", event.completedAt() != null ? event.completedAt() : Instant.now());

        return templateEngine.process("email/ticket-confirmation", context);
    }

    public void persistDevEmail(Long reservationId, String htmlContent) {
        try {
            File dir = new File("target", "generated-emails");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File emailFile = new File(dir, "ticket-" + reservationId + "-" + System.currentTimeMillis() + ".html");
            try (FileWriter writer = new FileWriter(emailFile, StandardCharsets.UTF_8)) {
                writer.write(htmlContent);
            }
            log.info("Dev email written to filesystem: {}", emailFile.getAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not write dev email file for reservationId={}: {}", reservationId, e.getMessage());
        }
    }
}
