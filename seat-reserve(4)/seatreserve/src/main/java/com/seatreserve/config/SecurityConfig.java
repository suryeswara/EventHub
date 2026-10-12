package com.seatreserve.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatreserve.security.RestAccessDeniedHandler;
import com.seatreserve.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new DefaultAuthenticationEventPublisher(applicationEventPublisher);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/logout")
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .defaultSuccessUrl("/events", true)
                .failureUrl("/login?error=true")
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
            )
            .authorizeHttpRequests(auth -> auth
                // Actuator endpoints: health and info are public probes; metrics/admin require ORGANIZER role
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("ORGANIZER")

                // Static resources & Swagger
                .requestMatchers("/", "/index.html", "/favicon.ico", "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()

                // Public browsing endpoints (HTML and REST)
                .requestMatchers(HttpMethod.GET, "/events/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/sessions/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/sessions/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/seats/**").permitAll()

                // Auth pages & endpoints
                .requestMatchers(HttpMethod.GET, "/login", "/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/logout").permitAll()

                // ORGANIZER-only actions (HTML and REST)
                .requestMatchers("/organizer/**").hasRole("ORGANIZER")
                .requestMatchers(HttpMethod.POST, "/events/*/sessions").hasRole("ORGANIZER")
                .requestMatchers("/sessions/*/seats/generate").hasRole("ORGANIZER")
                .requestMatchers(HttpMethod.POST, "/api/v1/events").hasRole("ORGANIZER")
                .requestMatchers(HttpMethod.POST, "/api/v1/events/*/sessions").hasRole("ORGANIZER")
                .requestMatchers(HttpMethod.POST, "/api/v1/sessions/*/seats/generate").hasRole("ORGANIZER")

                // CUSTOMER-only actions (HTML and REST)
                .requestMatchers(HttpMethod.POST, "/events/*/register").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/events/*/seats/*/hold").hasRole("CUSTOMER")
                .requestMatchers("/reservations/**").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/api/v1/events/*/register").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/api/v1/events/*/seats/*/hold").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/api/v1/reservations/*/confirm").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/api/v1/reservations/*/pay").hasRole("CUSTOMER")
                .requestMatchers("/api/v1/payments/**").hasRole("CUSTOMER")
                .requestMatchers("/notifications/**").authenticated()

                // All other requests require authentication
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
