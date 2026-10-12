package com.seatreserve.web;

import com.seatreserve.notification.Notification;
import com.seatreserve.notification.NotificationRepository;
import com.seatreserve.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
public class WebNotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public String listNotifications(@AuthenticationPrincipal AppUserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userDetails.getId());
        long unreadCount = notificationRepository.countByUserIdAndReadFalse(userDetails.getId());

        model.addAttribute("notifications", notifications);
        model.addAttribute("unreadCount", unreadCount);
        return "notifications/list";
    }

    @PostMapping("/{id}/read")
    public String markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        notificationRepository.findByIdAndUserId(id, userDetails.getId()).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });

        redirectAttributes.addFlashAttribute("successMessage", "Notification marked as read.");
        return "redirect:/notifications";
    }

    @PostMapping("/read-all")
    public String markAllAsRead(
            @AuthenticationPrincipal AppUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        List<Notification> unread = notificationRepository.findByUserIdOrderByCreatedAtDesc(userDetails.getId())
                .stream()
                .filter(n -> !n.isRead())
                .toList();

        for (Notification n : unread) {
            n.setRead(true);
        }
        notificationRepository.saveAll(unread);

        redirectAttributes.addFlashAttribute("successMessage", "All notifications marked as read.");
        return "redirect:/notifications";
    }
}
