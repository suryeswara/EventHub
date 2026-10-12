package com.seatreserve.web;

import com.seatreserve.common.HoldExpiredException;
import com.seatreserve.common.NotRegisteredException;
import com.seatreserve.common.SeatConflictException;
import com.seatreserve.payment.Payment;
import com.seatreserve.payment.PaymentDtos;
import com.seatreserve.payment.PaymentService;
import com.seatreserve.reservation.Reservation;
import com.seatreserve.reservation.ReservationRepository;
import com.seatreserve.reservation.ReservationService;
import com.seatreserve.seat.Seat;
import com.seatreserve.seat.SeatRepository;
import com.seatreserve.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebReservationController {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final PaymentService paymentService;

    @PostMapping("/events/{eventId}/seats/{seatId}/hold")
    public String holdSeat(
            @PathVariable Long eventId,
            @PathVariable Long seatId,
            @AuthenticationPrincipal AppUserDetails userDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Reservation reservation = reservationService.holdSeat(seatId, userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Temporary hold secured! Please confirm your reservation within the hold window.");
            return "redirect:/reservations/" + reservation.getId() + "/confirm";
        } catch (SeatConflictException ex) {
            log.info("Seat hold conflict for user {} on seat {}", userDetails.getId(), seatId);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Sorry! That seat was just claimed by another attendee. Please choose an available seat.");
            Seat seat = seatRepository.findById(seatId).orElse(null);
            if (seat != null && seat.getSession() != null) {
                return "redirect:/sessions/" + seat.getSession().getId();
            }
            return "redirect:/events/" + eventId;
        } catch (NotRegisteredException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "You must be registered for this event before holding session seats.");
            return "redirect:/events/" + eventId;
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/events/" + eventId;
        }
    }

    @GetMapping("/reservations/{id}/confirm")
    public String confirmReservationPage(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails userDetails,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Reservation reservation = reservationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NoSuchElementException("Reservation not found: " + id));

        // Ensure user can only view their own reservation confirmation
        if (reservation.getUser() != null && !reservation.getUser().getId().equals(userDetails.getId())) {
            log.warn("Access denied: user {} attempted viewing reservation {} owned by {}",
                    userDetails.getId(), id, reservation.getUser().getId());
            redirectAttributes.addFlashAttribute("errorMessage", "Access denied: You can only view your own reservations.");
            return "redirect:/reservations/my";
        }

        model.addAttribute("reservation", reservation);
        return "reservations/confirm";
    }

    @PostMapping("/reservations/{id}/confirm")
    public String handleConfirmReservation(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails userDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            String clientKey = "web-" + id + "-" + UUID.randomUUID();
            PaymentDtos.PaymentResponse paymentResponse = paymentService.initiatePayment(id, userDetails.getId(), clientKey);

            if (paymentResponse.status() == Payment.Status.SUCCEEDED) {
                log.info("Web payment and reservation confirmed: id={}, user={}", id, userDetails.getId());
                redirectAttributes.addFlashAttribute("successMessage",
                        "Payment successful! Reservation confirmed! Your official conference ticket has been issued.");
                return "redirect:/reservations/my";
            } else {
                log.warn("Web payment failed/declined: id={}, user={}", id, userDetails.getId());
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Payment declined by gateway. Your seat is still held temporarily — please retry checkout before your hold expires.");
                return "redirect:/reservations/" + id + "/confirm";
            }
        } catch (com.seatreserve.common.UnauthorizedReservationException ex) {
            log.warn("Web confirmation rejected (unauthorized): user {} attempted confirming reservation {}",
                    userDetails.getId(), id);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Access denied: You cannot confirm another attendee's reservation.");
            return "redirect:/reservations/my";
        } catch (HoldExpiredException ex) {
            log.info("Web confirmation failed due to expired hold: id={}", id);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Your hold expired before confirmation. The seat has been released back to availability.");
            return "redirect:/events";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/reservations/my";
        }
    }

    @GetMapping("/reservations/my")
    public String myReservations(
            @AuthenticationPrincipal AppUserDetails userDetails,
            Model model
    ) {
        List<Reservation> reservations = reservationRepository.findByUserIdWithDetails(userDetails.getId());
        model.addAttribute("reservations", reservations);
        return "reservations/my";
    }
}
