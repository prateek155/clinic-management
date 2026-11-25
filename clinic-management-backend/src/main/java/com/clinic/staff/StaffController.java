package com.clinic.staff;

import static com.clinic.staff.StaffDtos.*;

import com.clinic.security.FirebasePrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StaffController {
    private final StaffService staff;

    public StaffController(StaffService staff) { this.staff = staff; }

    @PostMapping("/doctor/staff/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationDto invite(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @Valid @RequestBody InvitationRequest request) {
        return staff.invite(principal, clinicId, request);
    }

    @GetMapping("/doctor/staff/invitations")
    public List<InvitationDto> invitations(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId) {
        return staff.invitations(principal, clinicId);
    }

    @DeleteMapping("/doctor/staff/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID invitationId) {
        staff.revokeInvitation(principal, clinicId, invitationId);
    }

    @PostMapping("/staff/invitations/accept")
    public StaffMemberDto accept(@AuthenticationPrincipal FirebasePrincipal principal,
            @Valid @RequestBody InvitationAcceptanceRequest request) {
        return staff.acceptInvitation(principal, request.token());
    }

    @GetMapping("/doctor/staff")
    public List<StaffMemberDto> members(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId) {
        return staff.members(principal, clinicId);
    }

    @PutMapping("/doctor/staff/{memberId}/permissions")
    public StaffMemberDto permissions(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID memberId,
            @Valid @RequestBody PermissionsRequest request) {
        return staff.updatePermissions(principal, clinicId, memberId, request);
    }

    @DeleteMapping("/doctor/staff/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID memberId) {
        staff.deactivate(principal, clinicId, memberId);
    }

    @GetMapping("/staff/dashboard")
    public StaffDashboardDto dashboard(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId) {
        return staff.dashboard(principal, clinicId);
    }

    @GetMapping("/staff/me")
    public StaffMemberDto currentStaff(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId) {
        return staff.currentStaff(principal, clinicId);
    }

    @GetMapping("/staff/appointments")
    public List<com.clinic.appointment.AppointmentDtos.AppointmentDto> appointments(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return staff.appointments(principal, clinicId, date);
    }

    @GetMapping("/staff/patients")
    public List<com.clinic.patient.PatientDtos.DoctorPatientDto> patients(
            @AuthenticationPrincipal FirebasePrincipal principal, @RequestParam UUID clinicId,
            @RequestParam(required = false) String q) {
        return staff.patients(principal, clinicId, q);
    }

    @PatchMapping("/staff/appointments/{appointmentId}/check-in")
    public com.clinic.appointment.AppointmentDtos.AppointmentDto checkIn(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID appointmentId) {
        return staff.checkIn(principal, clinicId, appointmentId);
    }
}
