package com.seatreserve.web;

import com.seatreserve.notification.NotificationRepository;
import com.seatreserve.security.AppUserDetails;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackages = "com.seatreserve.web")
public class NotificationControllerAdvice {

    private final ObjectProvider<NotificationRepository> notificationRepositoryProvider;

    public NotificationControllerAdvice(ObjectProvider<NotificationRepository> notificationRepositoryProvider) {
        this.notificationRepositoryProvider = notificationRepositoryProvider;
    }

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(@AuthenticationPrincipal AppUserDetails userDetails) {
        if (userDetails != null && userDetails.getId() != null) {
            NotificationRepository repo = notificationRepositoryProvider.getIfAvailable();
            if (repo != null) {
                return repo.countByUserIdAndReadFalse(userDetails.getId());
            }
        }
        return 0;
    }
}
