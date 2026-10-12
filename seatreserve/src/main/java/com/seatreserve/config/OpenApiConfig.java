package com.seatreserve.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI seatReserveOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("seat-reserve API")
                        .description("Multi-tenant event seat reservation system. "
                                + "Core guarantee: concurrent hold requests for the same seat "
                                + "never result in a double booking (see SeatRepository.findByIdForUpdate "
                                + "and ReservationService.holdSeat for the locking strategy).")
                        .version("v0.1.0")
                        .contact(new Contact().name("seat-reserve")));
    }
}
