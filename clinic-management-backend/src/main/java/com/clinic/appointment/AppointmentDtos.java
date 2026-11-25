package com.clinic.appointment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AppointmentDtos {
    private AppointmentDtos() {}

    public record ClinicDto(UUID id, String name, String city, String region, String timezone) {}

    public record DoctorDto(
            UUID clinicId,
            String clinicName,
            UUID doctorProfileId,
            String fullName,
            String specialization,
            String qualification,
            BigDecimal consultationFee,
            int consultationDurationMinutes) {}

    public record SlotDto(UUID id, OffsetDateTime startsAt, OffsetDateTime endsAt) {}

    public record AppointmentDto(
            UUID id,
            UUID clinicId,
            UUID clinicPatientId,
            String clinicName,
            UUID doctorProfileId,
            String doctorName,
            String specialization,
            String patientCode,
            String patientName,
            String patientEmail,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String status,
            String reason,
            String cancellationReason,
            OffsetDateTime createdAt) {}

    public record ScheduleBreakDto(LocalTime startsAt, LocalTime endsAt, String label) {}

    public record ScheduleDto(
            UUID id,
            UUID clinicId,
            int weekday,
            LocalTime startsAt,
            LocalTime endsAt,
            int slotDurationMinutes,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            boolean active,
            List<ScheduleBreakDto> breaks) {}

    public record ScheduleExceptionDto(UUID id, UUID clinicId, LocalDate exceptionDate, String reason) {}

    public record DashboardDto(
            long totalPatients,
            long todayAppointments,
            long pendingAppointments,
            long completedAppointments,
            long cancelledAppointments) {}

    public record BookingRequest(
            @NotNull UUID clinicId,
            @NotNull UUID doctorProfileId,
            @NotNull UUID slotId,
            UUID familyMemberId,
            @Size(max = 500) String reason) {}

    public record UpdateAppointmentRequest(@Size(max = 500) String reason) {}

    public record RescheduleRequest(@NotNull UUID slotId) {}

    public record CancelRequest(@Size(max = 500) String reason) {}

    public record StatusRequest(
            @NotBlank
            @Pattern(regexp = "CONFIRMED|REJECTED|CANCELLED|CHECKED_IN|IN_CONSULTATION|COMPLETED|NO_SHOW")
            String status,
            @Size(max = 500) String reason) {}

    public record ScheduleBreakRequest(
            @NotNull LocalTime startsAt,
            @NotNull LocalTime endsAt,
            @Size(max = 80) String label) {}

    public record ScheduleRequest(
            @NotNull UUID clinicId,
            @Min(0) @Max(6) int weekday,
            @NotNull LocalTime startsAt,
            @NotNull LocalTime endsAt,
            @Min(5) @Max(240) int slotDurationMinutes,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            List<@Valid ScheduleBreakRequest> breaks) {}

    public record ScheduleExceptionRequest(@NotNull UUID clinicId, @NotNull LocalDate exceptionDate,
            @Size(max = 240) String reason) {}
}
