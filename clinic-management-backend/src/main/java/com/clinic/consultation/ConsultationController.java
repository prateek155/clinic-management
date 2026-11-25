package com.clinic.consultation;

import static com.clinic.consultation.ConsultationDtos.*;

import com.clinic.security.FirebasePrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ConsultationController {
    private final ConsultationService consultations;

    public ConsultationController(ConsultationService consultations) {
        this.consultations = consultations;
    }

    @PostMapping("/doctor/appointments/{appointmentId}/consultation")
    @ResponseStatus(HttpStatus.CREATED)
    public DoctorConsultationDto start(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID appointmentId,
            @RequestParam UUID clinicId) {
        return consultations.start(principal, clinicId, appointmentId);
    }

    @GetMapping("/doctor/appointments/{appointmentId}/consultation")
    public DoctorConsultationDto get(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID appointmentId,
            @RequestParam UUID clinicId) {
        return consultations.doctorConsultation(principal, clinicId, appointmentId);
    }

    @PutMapping("/doctor/consultations/{consultationId}")
    public DoctorConsultationDto saveDraft(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID consultationId,
            @RequestParam UUID clinicId,
            @Valid @RequestBody ConsultationRequest request) {
        return consultations.saveDraft(principal, clinicId, consultationId, request);
    }

    @PatchMapping("/doctor/consultations/{consultationId}/finalize")
    public DoctorConsultationDto finalizeConsultation(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID consultationId,
            @RequestParam UUID clinicId) {
        return consultations.finalizeConsultation(principal, clinicId, consultationId);
    }

    @PutMapping("/doctor/consultations/{consultationId}/prescription")
    public PrescriptionDto savePrescription(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID consultationId,
            @RequestParam UUID clinicId,
            @Valid @RequestBody PrescriptionRequest request) {
        return consultations.savePrescription(principal, clinicId, consultationId, request);
    }

    @PatchMapping("/doctor/prescriptions/{prescriptionId}/finalize")
    public PrescriptionDto finalizePrescription(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID prescriptionId,
            @RequestParam UUID clinicId) {
        return consultations.finalizePrescription(principal, clinicId, prescriptionId);
    }

    @PostMapping("/doctor/prescriptions/{prescriptionId}/amendments")
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionDto amendPrescription(@AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID prescriptionId, @RequestParam UUID clinicId,
            @Valid @RequestBody PrescriptionAmendmentRequest request) {
        return consultations.amendPrescription(principal, clinicId, prescriptionId, request);
    }

    @GetMapping("/doctor/consultations/{consultationId}/prescription")
    public PrescriptionDto doctorPrescription(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID consultationId,
            @RequestParam UUID clinicId) {
        return consultations.doctorPrescription(principal, clinicId, consultationId);
    }

    @GetMapping("/patient/consultations")
    public List<PatientConsultationDto> patientConsultations(@AuthenticationPrincipal FirebasePrincipal principal) {
        return consultations.patientConsultations(principal);
    }

    @GetMapping("/patient/prescriptions")
    public List<PatientPrescriptionDto> patientPrescriptions(@AuthenticationPrincipal FirebasePrincipal principal) {
        return consultations.patientPrescriptions(principal);
    }
}
