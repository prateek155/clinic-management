package com.clinic.consultation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ConsultationDtos {
    private ConsultationDtos() {}

    public record VitalsRequest(
            @DecimalMin("1") @DecimalMax("300") BigDecimal heightCm,
            @DecimalMin("1") @DecimalMax("500") BigDecimal weightKg,
            @DecimalMin("25") @DecimalMax("45") BigDecimal temperatureC,
            @Min(1) @Max(300) Integer systolicBp,
            @Min(1) @Max(200) Integer diastolicBp,
            @Min(1) @Max(300) Integer pulseBpm,
            @DecimalMin("0") @DecimalMax("100") BigDecimal oxygenSaturation) {}

    public record ConsultationRequest(
            @Size(max = 4000) String symptoms,
            @Size(max = 4000) String observations,
            @Size(max = 4000) String diagnosis,
            @Size(max = 4000) String treatmentPlan,
            @Size(max = 4000) String doctorNotes,
            @Size(max = 500) String followUpInstructions,
            @Valid VitalsRequest vitals,
            OffsetDateTime followUpDueAt) {}

    public record DoctorConsultationDto(
            UUID id,
            UUID appointmentId,
            UUID clinicPatientId,
            String patientName,
            String patientCode,
            String symptoms,
            String observations,
            String diagnosis,
            String treatmentPlan,
            String doctorNotes,
            String followUpInstructions,
            OffsetDateTime startedAt,
            OffsetDateTime finalizedAt,
            VitalsRequest vitals,
            OffsetDateTime followUpDueAt,
            String followUpStatus) {}

    public record MedicineRequest(
            @NotBlank @Size(max = 200) String medicineName,
            @Size(max = 200) String genericName,
            @NotBlank @Size(max = 120) String dosage,
            @Size(max = 48) String doseUnit,
            @NotBlank @Size(max = 120) String frequency,
            @NotBlank @Size(max = 120) String duration,
            @Size(max = 80) String route,
            @Size(max = 24) String foodInstruction,
            @Size(max = 80) String quantity,
            @Size(max = 2000) String additionalInstructions,
            LocalDate startsOn,
            LocalDate endsOn) {}

    public record PrescriptionRequest(
            @Size(max = 2000) String instructions,
            @NotEmpty @Size(max = 30) List<@Valid MedicineRequest> items) {}

    public record PrescriptionAmendmentRequest(@NotBlank @Size(max = 500) String reason,
            @NotNull @Valid PrescriptionRequest changes) {}

    public record PrescriptionItemDto(
            UUID id,
            String medicineName,
            String genericName,
            String dosage,
            String doseUnit,
            String frequency,
            String duration,
            String route,
            String foodInstruction,
            String quantity,
            String additionalInstructions,
            LocalDate startsOn,
            LocalDate endsOn) {}

    public record PrescriptionDto(
            UUID id,
            UUID consultationId,
            String status,
            String instructions,
            OffsetDateTime createdAt,
            OffsetDateTime finalizedAt,
            List<PrescriptionItemDto> items,
            List<PrescriptionAmendmentDto> amendments) {}

    public record PrescriptionAmendmentDto(UUID id, String reason, OffsetDateTime createdAt,
            String instructions, List<PrescriptionItemDto> items) {}

    public record PrescriptionContents(String instructions, List<PrescriptionItemDto> items) {}

    public record PatientConsultationDto(
            UUID id,
            String clinicName,
            String doctorName,
            OffsetDateTime startedAt,
            OffsetDateTime finalizedAt,
            String symptoms,
            String observations,
            String diagnosis,
            String treatmentPlan,
            String followUpInstructions,
            OffsetDateTime followUpDueAt,
            VitalsRequest vitals) {}

    public record PatientPrescriptionDto(
            UUID id,
            String clinicName,
            String doctorName,
            String status,
            String instructions,
            OffsetDateTime createdAt,
            OffsetDateTime finalizedAt,
            List<PrescriptionItemDto> items,
            List<PrescriptionAmendmentDto> amendments) {}
}
