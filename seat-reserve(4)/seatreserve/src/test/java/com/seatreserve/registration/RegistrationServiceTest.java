package com.seatreserve.registration;

import com.seatreserve.event.Event;
import com.seatreserve.event.EventRepository;
import com.seatreserve.user.User;
import com.seatreserve.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static com.seatreserve.registration.RegistrationDtos.RegistrationResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock private AttendeeRegistrationRepository attendeeRegistrationRepository;
    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RegistrationService registrationService;

    private Event testEvent;
    private User testUser;
    private AttendeeRegistration existingRegistration;

    @BeforeEach
    void setUp() {
        testEvent = Event.builder().id(10L).name("DevCon 2026").build();
        testUser = User.builder().id(20L).email("dev@test.com").build();
        existingRegistration = AttendeeRegistration.builder()
                .id(100L)
                .event(testEvent)
                .user(testUser)
                .registeredAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Calling registerForEvent twice for same user/event is idempotent and returns existing registration")
    void registerForEvent_isIdempotent_whenAlreadyRegistered() {
        // Arrange: first check returns existing registration
        when(attendeeRegistrationRepository.findByEventIdAndUserId(10L, 20L))
                .thenReturn(Optional.of(existingRegistration));

        // Act: register again
        RegistrationResponse response = registrationService.registerForEvent(10L, 20L);

        // Assert: returns existing without attempting save
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.eventId()).isEqualTo(10L);
        assertThat(response.userId()).isEqualTo(20L);
        verify(attendeeRegistrationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Saves and returns new AttendeeRegistration when user is not yet registered")
    void registerForEvent_savesNewRegistration_whenNotRegistered() {
        when(attendeeRegistrationRepository.findByEventIdAndUserId(10L, 20L))
                .thenReturn(Optional.empty());
        when(eventRepository.findById(10L)).thenReturn(Optional.of(testEvent));
        when(userRepository.findById(20L)).thenReturn(Optional.of(testUser));
        when(attendeeRegistrationRepository.save(any(AttendeeRegistration.class)))
                .thenReturn(existingRegistration);

        RegistrationResponse response = registrationService.registerForEvent(10L, 20L);

        assertThat(response.id()).isEqualTo(100L);
        verify(attendeeRegistrationRepository).save(any(AttendeeRegistration.class));
    }

    @Test
    @DisplayName("Handles concurrent insert race by catching DataIntegrityViolationException and returning registration")
    void registerForEvent_handlesConcurrentRaceGracefully() {
        when(attendeeRegistrationRepository.findByEventIdAndUserId(10L, 20L))
                .thenReturn(Optional.empty()) // Initial check: not found
                .thenReturn(Optional.of(existingRegistration)); // Second check after constraint race
        when(eventRepository.findById(10L)).thenReturn(Optional.of(testEvent));
        when(userRepository.findById(20L)).thenReturn(Optional.of(testUser));
        when(attendeeRegistrationRepository.save(any(AttendeeRegistration.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        RegistrationResponse response = registrationService.registerForEvent(10L, 20L);

        assertThat(response.id()).isEqualTo(100L);
    }

    @Test
    @DisplayName("isRegistered returns true when registration exists, false otherwise")
    void isRegistered_verifiesPresence() {
        when(attendeeRegistrationRepository.findByEventIdAndUserId(10L, 20L))
                .thenReturn(Optional.of(existingRegistration));
        when(attendeeRegistrationRepository.findByEventIdAndUserId(10L, 99L))
                .thenReturn(Optional.empty());

        assertThat(registrationService.isRegistered(10L, 20L)).isTrue();
        assertThat(registrationService.isRegistered(10L, 99L)).isFalse();
    }
}
