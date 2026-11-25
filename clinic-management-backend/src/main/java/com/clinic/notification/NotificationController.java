package com.clinic.notification;

import static com.clinic.notification.NotificationDtos.*;

import com.clinic.security.FirebasePrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) { this.notifications = notifications; }

    @GetMapping
    public List<NotificationDto> list(@AuthenticationPrincipal FirebasePrincipal principal) {
        return notifications.list(principal);
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationDto markRead(@AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID notificationId) { return notifications.markRead(principal, notificationId); }

    @PatchMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal FirebasePrincipal principal) { notifications.markAllRead(principal); }

    @GetMapping("/preferences")
    public PreferenceDto preferences(@AuthenticationPrincipal FirebasePrincipal principal) {
        return notifications.preferences(principal);
    }

    @PutMapping("/preferences")
    public PreferenceDto updatePreferences(@AuthenticationPrincipal FirebasePrincipal principal,
            @Valid @RequestBody PreferenceRequest request) { return notifications.updatePreferences(principal, request); }
}
