package com.seatreserve.registration;

import com.seatreserve.common.event.RegistrationCreatedEvent;
import com.seatreserve.event.Event;
import com.seatreserve.event.EventRepository;
import com.seatreserve.user.User;
import com.seatreserve.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

import static com.seatreserve.registration.RegistrationDtos.RegistrationResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final AttendeeRegistrationRepository attendeeRegistrationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Registers a user for an event. Idempotent: if the user is already registered,
     * returns the existing registration cleanly instead of throwing a constraint violation.
     */
    @Transactional
    public RegistrationResponse registerForEvent(Long eventId, Long userId) {
        Optional<AttendeeRegistration> existing = attendeeRegistrationRepository.findByEventIdAndUserId(eventId, userId);
        if (existing.isPresent()) {
            log.info("Idempotent registration: user {} already registered for event {} (registrationId={})",
                    userId, eventId, existing.get().getId());
            return RegistrationResponse.from(existing.get());
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + eventId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        try {
            AttendeeRegistration registration = AttendeeRegistration.builder()
                    .event(event)
                    .user(user)
                    .build();
            AttendeeRegistration saved = attendeeRegistrationRepository.save(registration);
            log.info("New registration created: registrationId={}, eventId={}, userId={}",
                    saved.getId(), eventId, userId);
            eventPublisher.publishEvent(new RegistrationCreatedEvent(
                    saved.getId(),
                    userId,
                    eventId,
                    event.getName(),
                    saved.getRegisteredAt()
            ));
            return RegistrationResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            // Handle concurrent insert race cleanly by re-fetching existing registration
            log.info("Concurrent registration race handled idempotently: eventId={}, userId={}", eventId, userId);
            return RegistrationResponse.from(
                    attendeeRegistrationRepository.findByEventIdAndUserId(eventId, userId)
                            .orElseThrow(() -> e)
            );
        }
    }

    @Transactional(readOnly = true)
    public boolean isRegistered(Long eventId, Long userId) {
        return attendeeRegistrationRepository.findByEventIdAndUserId(eventId, userId).isPresent();
    }
}
