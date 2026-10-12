package com.seatreserve.event;

import com.seatreserve.user.User;
import com.seatreserve.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

import static com.seatreserve.event.EventDtos.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Transactional
    public EventResponse createEvent(CreateEventRequest request, Long organizerId) {
        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new NoSuchElementException("Organizer not found: " + organizerId));

        Event event = Event.builder()
                .organizer(organizer)
                .name(request.name())
                .venue(request.venue())
                .startTime(request.startTime())
                .build();

        Event saved = eventRepository.save(event);
        log.info("Event created: eventId={}, name={}, organizerId={}", saved.getId(), saved.getName(), organizerId);
        return EventResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + eventId));
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents() {
        return eventRepository.findAll().stream()
                .map(EventResponse::from)
                .toList();
    }
}
