package com.clinic.staff;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class StaffDtos {
    private StaffDtos() {}

    public record InvitationRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @Size(max = 100) String designation,
            @NotEmpty Set<@NotBlank @Size(max = 64) String> permissions) {}

    public record InvitationAcceptanceRequest(@NotBlank String token) {}

    public record InvitationDto(UUID id, String email, String designation, List<String> permissions,
            OffsetDateTime expiresAt, OffsetDateTime acceptedAt, OffsetDateTime revokedAt, String token) {}

    public record StaffMemberDto(UUID memberId, UUID clinicId, String fullName, String email,
            String designation, boolean active, List<String> permissions) {}

    public record PermissionsRequest(@NotEmpty Set<@NotBlank @Size(max = 64) String> permissions) {}

    public record StaffDashboardDto(long todayAppointments, long waitingPatients, long completedToday, long totalPatients) {}
}
