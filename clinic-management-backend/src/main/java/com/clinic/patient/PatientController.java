package com.clinic.patient;

import static com.clinic.patient.PatientDtos.*;

import com.clinic.security.FirebasePrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PatientController {
    private final PatientService patients;

    public PatientController(PatientService patients) { this.patients = patients; }

    @GetMapping("/patient/profile")
    public ProfileDto profile(@AuthenticationPrincipal FirebasePrincipal principal) { return patients.profile(principal); }

    @PutMapping("/patient/profile")
    public ProfileDto updateProfile(@AuthenticationPrincipal FirebasePrincipal principal,
            @Valid @RequestBody ProfileRequest request) { return patients.updateProfile(principal, request); }

    @GetMapping("/patient/family")
    public List<FamilyMemberDto> family(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId) { return patients.family(principal, clinicId); }

    @PostMapping("/patient/family")
    public FamilyMemberDto addFamily(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @Valid @RequestBody FamilyRequest request) {
        return patients.addFamily(principal, clinicId, request);
    }

    @PutMapping("/patient/family/{familyMemberId}")
    public FamilyMemberDto updateFamily(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID familyMemberId, @Valid @RequestBody FamilyRequest request) {
        return patients.updateFamily(principal, clinicId, familyMemberId, request);
    }

    @DeleteMapping("/patient/family/{familyMemberId}")
    public FamilyMemberDto revokeFamily(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID familyMemberId) {
        return patients.revokeFamily(principal, clinicId, familyMemberId);
    }

    @GetMapping("/doctor/patients")
    public List<DoctorPatientDto> doctorPatients(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @RequestParam(required = false) String q) {
        return patients.doctorPatients(principal, clinicId, q);
    }

    @GetMapping("/doctor/patients/{clinicPatientId}")
    public DoctorPatientDetailDto doctorPatient(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID clinicPatientId) {
        return patients.doctorPatient(principal, clinicId, clinicPatientId);
    }

    @PostMapping("/doctor/patients/{clinicPatientId}/medical-history")
    public MedicalHistoryDto addHistory(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID clinicPatientId,
            @Valid @RequestBody MedicalHistoryRequest request) {
        return patients.addMedicalHistory(principal, clinicId, clinicPatientId, request);
    }

    @PostMapping("/doctor/patients/{clinicPatientId}/allergies")
    public AllergyDto addAllergy(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID clinicPatientId,
            @Valid @RequestBody AllergyRequest request) {
        return patients.addAllergy(principal, clinicId, clinicPatientId, request);
    }
}
