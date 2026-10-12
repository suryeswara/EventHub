package com.seatreserve.web;

import com.seatreserve.event.EventDtos.CreateEventRequest;
import com.seatreserve.event.EventDtos.EventResponse;
import com.seatreserve.event.EventService;
import com.seatreserve.registration.RegistrationService;
import com.seatreserve.security.AppUserDetails;
import com.seatreserve.session.SessionDtos.CreateSessionRequest;
import com.seatreserve.session.SessionDtos.SessionResponse;
import com.seatreserve.session.SessionService;
import com.seatreserve.web.WebForms.CreateEventForm;
import com.seatreserve.web.WebForms.CreateSessionForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebEventController {

    private final EventService eventService;
    private final SessionService sessionService;
    private final RegistrationService registrationService;

    @GetMapping("/")
    public String index() {
        return "redirect:/events";
    }

    @GetMapping("/events")
    public String listEvents(Model model) {
        List<EventResponse> events = eventService.listEvents();
        model.addAttribute("events", events);
        return "events/list";
    }

    @GetMapping("/events/{id}")
    public String eventDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails userDetails,
            Model model
    ) {
        EventResponse event = eventService.getEvent(id);
        List<SessionResponse> sessions = sessionService.listSessionsForEvent(id);

        boolean isRegistered = false;
        if (userDetails != null) {
            isRegistered = registrationService.isRegistered(id, userDetails.getId());
        }

        model.addAttribute("event", event);
        model.addAttribute("sessions", sessions);
        model.addAttribute("isRegistered", isRegistered);
        return "events/detail";
    }

    @PostMapping("/events/{id}/register")
    public String registerForEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails userDetails,
            RedirectAttributes redirectAttributes
    ) {
        registrationService.registerForEvent(id, userDetails.getId());
        redirectAttributes.addFlashAttribute("successMessage",
                "Successfully registered for this conference! You can now reserve session seats.");
        return "redirect:/events/" + id;
    }

    @GetMapping("/organizer/events/new")
    public String newEventForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CreateEventForm("", "", LocalDateTime.now().plusDays(7)));
        }
        return "organizer/create-event";
    }

    @PostMapping("/organizer/events")
    public String handleCreateEvent(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @Valid @ModelAttribute("form") CreateEventForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "organizer/create-event";
        }

        CreateEventRequest request = new CreateEventRequest(
                form.name(),
                form.venue(),
                form.startTime().atZone(ZoneId.systemDefault()).toInstant()
        );
        EventResponse created = eventService.createEvent(request, userDetails.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Event created successfully!");
        return "redirect:/events/" + created.id();
    }

    @GetMapping("/events/{id}/sessions/new")
    public String newSessionForm(@PathVariable Long id, Model model) {
        model.addAttribute("eventId", id);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CreateSessionForm(
                    "", "",
                    LocalDateTime.now().plusDays(7).withHour(10).withMinute(0),
                    LocalDateTime.now().plusDays(7).withHour(11).withMinute(30),
                    30,
                    new BigDecimal("49.00")
            ));
        }
        return "organizer/create-session";
    }

    @PostMapping("/events/{id}/sessions")
    public String handleCreateSession(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails userDetails,
            @Valid @ModelAttribute("form") CreateSessionForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("eventId", id);
            return "organizer/create-session";
        }

        try {
            CreateSessionRequest request = new CreateSessionRequest(
                    form.title(),
                    form.description(),
                    form.startTime().atZone(ZoneId.systemDefault()).toInstant(),
                    form.endTime().atZone(ZoneId.systemDefault()).toInstant(),
                    form.capacity(),
                    form.price() != null ? form.price() : BigDecimal.ZERO
            );
            sessionService.createSession(id, request, userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Session added successfully!");
            return "redirect:/events/" + id;
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/events/" + id;
        }
    }
}
