package com.seatreserve;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // needed for the hold-expiry sweep job (Day 8-9)
@EnableAsync      // needed for async email generation
public class SeatReserveApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeatReserveApplication.class, args);
    }
}
