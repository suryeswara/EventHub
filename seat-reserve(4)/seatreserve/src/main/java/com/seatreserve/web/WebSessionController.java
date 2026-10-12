package com.seatreserve.web;

import com.seatreserve.registration.RegistrationService;
import com.seatreserve.seat.Seat;
import com.seatreserve.seat.SeatRepository;
import com.seatreserve.security.AppUserDetails;
import com.seatreserve.session.SessionDtos.GenerateSeatsRequest;
import com.seatreserve.session.SessionDtos.SectionSpec;
import com.seatreserve.session.SessionDtos.SessionResponse;
import com.seatreserve.session.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSessionController {

    private final SessionService sessionService;
    private final SeatRepository seatRepository;
    private final RegistrationService registrationService;

    @GetMapping("/sessions/{id}")
    public String sessionSeatMap(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails userDetails,
            Model model
    ) {
        SessionResponse session = sessionService.getSession(id);
        List<Seat> seats = seatRepository.findBySessionIdOrderBySectionAscRowLabelAscSeatNumberAsc(id);

        // Group seats by Section -> Row for visual seat grid rendering
        Map<String, Map<String, List<Seat>>> sectionMap = seats.stream()
                .collect(Collectors.groupingBy(
                        Seat::getSection,
                        LinkedHashMap::new,
                        Collectors.groupingBy(Seat::getRowLabel, LinkedHashMap::new, Collectors.toList())
                ));

        boolean isRegistered = false;
        if (userDetails != null && session.eventId() != null) {
            isRegistered = registrationService.isRegistered(session.eventId(), userDetails.getId());
        }

        model.addAttribute("eventSession", session);
        model.addAttribute("seats", seats);
        model.addAttribute("sectionMap", sectionMap);
        model.addAttribute("isRegistered", isRegistered);
        return "sessions/detail";
    }

    @GetMapping("/sessions/{id}/seats/generate")
    public String generateSeatsForm(@PathVariable Long id, Model model) {
        SessionResponse session = sessionService.getSession(id);
        model.addAttribute("eventSession", session);
        return "organizer/generate-seats";
    }

    @PostMapping("/sessions/{id}/seats/generate")
    public String handleGenerateSeats(
            @PathVariable Long id,
            @RequestParam String section,
            @RequestParam int rows,
            @RequestParam int seatsPerRow,
            @AuthenticationPrincipal AppUserDetails userDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            SectionSpec spec = new SectionSpec(section, rows, seatsPerRow);
            GenerateSeatsRequest request = new GenerateSeatsRequest(List.of(spec));
            sessionService.generateSeats(id, request, userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Generated " + (rows * seatsPerRow) + " seats successfully!");
            return "redirect:/sessions/" + id;
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/sessions/" + id + "/seats/generate";
        }
    }
}
