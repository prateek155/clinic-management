package com.clinic.patient;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PatientDtos {
    private PatientDtos() {}

    public record ProfileDto(String fullName, String email, String phone, LocalDate dateOfBirth, String sexAtBirth,
            String preferredName, String emergencyContactName, String emergencyContactPhone) {}

    public record ProfileRequest(@NotBlank @Size(min = 2, max = 120) String fullName,
            @Size(max = 32) String phone, LocalDate dateOfBirth,
            @Pattern(regexp = "|FEMALE|MALE|INTERSEX|UNKNOWN|PREFER_NOT_TO_SAY") String sexAtBirth,
            @Size(max = 120) String preferredName, @Size(max = 120) String emergencyContactName,
            @Size(max = 32) String emergencyContactPhone) {}

    public record FamilyMemberDto(UUID id, String fullName, String relationship, LocalDate dateOfBirth,
            String phone, boolean authorizedByPatient) {}

    public record FamilyRequest(@NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(max = 48) String relationship, LocalDate dateOfBirth, @Size(max = 32) String phone) {}

    public record DoctorPatientDto(UUID clinicPatientId, String patientCode, String fullName, String email,
            String phone, long appointmentCount, OffsetDateTime lastAppointmentAt) {}

    public record DoctorPatientDetailDto(DoctorPatientDto patient, ProfileDto profile,
            List<MedicalHistoryDto> history, List<AllergyDto> allergies, List<VisitSummaryDto> recentVisits) {}

    public record MedicalHistoryDto(UUID id, String category, String description, OffsetDateTime recordedAt, String recordedBy) {}
    public record MedicalHistoryRequest(@NotBlank @Size(max = 48) String category, @NotBlank @Size(max = 4000) String description) {}
    public record AllergyDto(UUID id, String allergen, String reaction, String severity, String notes, OffsetDateTime createdAt) {}
    public record AllergyRequest(@NotBlank @Size(max = 160) String allergen, @Size(max = 300) String reaction,
            @Pattern(regexp = "|MILD|MODERATE|SEVERE|UNKNOWN") String severity, @Size(max = 2000) String notes) {}
    public record VisitSummaryDto(UUID id, OffsetDateTime startedAt, OffsetDateTime finalizedAt,
            String symptoms, String diagnosis, String treatmentPlan, String doctorNotes) {}
}
