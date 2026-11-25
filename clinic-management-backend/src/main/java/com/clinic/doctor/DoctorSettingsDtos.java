package com.clinic.doctor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public final class DoctorSettingsDtos {
    private DoctorSettingsDtos() {}

    public record SettingsDto(UUID clinicId, String clinicName, String addressLine, String city, String region,
            String postalCode, String phone, String timezone, String specialization, String qualification,
            String registrationNumber, BigDecimal consultationFee, int consultationDurationMinutes,
            int cancellationHours, int bookingWindowDays) {}

    public record SettingsRequest(@NotBlank @Size(max = 160) String clinicName,
            @Size(max = 300) String addressLine, @Size(max = 100) String city, @Size(max = 100) String region,
            @Size(max = 24) String postalCode, @Size(max = 32) String phone,
            @NotBlank @Size(max = 64) String timezone, @Size(max = 120) String specialization,
            @Size(max = 240) String qualification, @Size(max = 80) String registrationNumber,
            @NotNull @DecimalMin("0.00") BigDecimal consultationFee,
            @NotNull @Min(5) @Max(240) Integer consultationDurationMinutes,
            @NotNull @Min(0) @Max(720) Integer cancellationHours,
            @NotNull @Min(1) @Max(365) Integer bookingWindowDays) {}

    public record ServiceDto(UUID id, String name, String description, BigDecimal fee, boolean active) {}

    public record ServiceRequest(@NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description, @NotNull @DecimalMin("0.00") BigDecimal fee) {}
}
