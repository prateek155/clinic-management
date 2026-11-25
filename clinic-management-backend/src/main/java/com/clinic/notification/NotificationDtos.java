package com.clinic.notification;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() {}
    public record NotificationDto(UUID id, UUID clinicId, String category, String title, String body,
            OffsetDateTime createdAt, OffsetDateTime readAt) {}
    public record PreferenceDto(boolean emailEnabled, boolean smsEnabled, boolean whatsappEnabled) {}
    public record PreferenceRequest(@NotNull Boolean emailEnabled, @NotNull Boolean smsEnabled, @NotNull Boolean whatsappEnabled) {}
}
