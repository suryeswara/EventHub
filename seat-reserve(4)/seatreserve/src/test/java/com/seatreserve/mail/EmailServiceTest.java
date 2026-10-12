package com.seatreserve.mail;

import com.seatreserve.common.event.PaymentSucceededEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EmailServiceTest {

    @DynamicPropertySource
    static void configureProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/seatreserve_test");
        registry.add("spring.datasource.username", () -> "seatreserve");
        registry.add("spring.datasource.password", () -> "seatreserve");
    }

    @Autowired
    private EmailService emailService;

    @Test
    @DisplayName("Email template renders correct event, session, seat, and price details")
    void testEmailTemplateGeneration() {
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                999L,
                888L,
                111L,
                "customer@cloudsummit.dev",
                new BigDecimal("149.00"),
                "USD",
                "DevCon 2026",
                "Advanced Kubernetes Security",
                "VIP-R1-S5",
                Instant.now()
        );

        String html = emailService.generateTicketConfirmationHtml(event);

        assertThat(html).isNotNull();
        assertThat(html).contains("DevCon 2026");
        assertThat(html).contains("Advanced Kubernetes Security");
        assertThat(html).contains("VIP-R1-S5");
        assertThat(html).contains("149.00 USD");
        assertThat(html).contains("#888");
        assertThat(html).contains("#999");
    }

    @Test
    @DisplayName("Async email processing writes inspectable dev file to target/generated-emails/")
    void testDevEmailPersistence() {
        long resId = 777L;
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                1001L,
                resId,
                2002L,
                "dev@seatreserve.com",
                new BigDecimal("99.00"),
                "USD",
                "Spring Boot Summit",
                "Deep Dive into Concurrency",
                "Balcony-2-12",
                Instant.now()
        );

        emailService.processEmail(event);

        File dir = new File("target/generated-emails");
        assertThat(dir.exists()).isTrue();

        File[] matches = dir.listFiles((d, name) -> name.startsWith("ticket-" + resId));
        assertThat(matches).isNotEmpty();
        assertThat(matches[0].length()).isGreaterThan(100);
    }
}
