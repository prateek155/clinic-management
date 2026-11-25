package com.clinic.appointment;

import static com.clinic.appointment.AppointmentDtos.*;

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
public class AppointmentController {
    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @GetMapping("/clinics")
    public List<ClinicDto> clinics() {
        return appointments.clinics();
    }

    @GetMapping("/clinics/{clinicId}/doctors")
    public List<DoctorDto> doctors(@PathVariable UUID clinicId) {
        return appointments.doctors(clinicId);
    }

    @GetMapping("/doctors/{doctorProfileId}/slots")
    public List<SlotDto> availableSlots(
            @PathVariable UUID doctorProfileId,
            @RequestParam UUID clinicId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointments.availableSlots(clinicId, doctorProfileId, date);
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentDto book(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @Valid @RequestBody BookingRequest request) {
        return appointments.book(principal, request);
    }

    @GetMapping("/appointments/my")
    public List<AppointmentDto> myAppointments(@AuthenticationPrincipal FirebasePrincipal principal) {
        return appointments.patientAppointments(principal);
    }

    @GetMapping("/appointments/{appointmentId}")
    public AppointmentDto patientAppointment(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID appointmentId) {
        return appointments.patientAppointment(principal, appointmentId);
    }

    @PutMapping("/appointments/{appointmentId}")
    public AppointmentDto updateReason(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody UpdateAppointmentRequest request) {
        return appointments.updateReason(principal, appointmentId, request);
    }

    @PatchMapping("/appointments/{appointmentId}/cancel")
    public AppointmentDto cancel(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID appointmentId,
            @RequestBody(required = false) CancelRequest request) {
        return appointments.cancelByPatient(principal, appointmentId, request);
    }

    @PutMapping("/appointments/{appointmentId}/reschedule")
    public AppointmentDto reschedule(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody RescheduleRequest request) {
        return appointments.rescheduleByPatient(principal, appointmentId, request);
    }

    @GetMapping("/doctor/dashboard")
    public DashboardDto doctorDashboard(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId) {
        return appointments.doctorDashboard(principal, clinicId);
    }

    @GetMapping("/doctor/appointments")
    public List<AppointmentDto> doctorAppointments(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return appointments.doctorAppointments(principal, clinicId, from, to);
    }

    @GetMapping("/doctor/appointments/{appointmentId}")
    public AppointmentDto doctorAppointment(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID appointmentId,
            @RequestParam UUID clinicId) {
        return appointments.doctorAppointment(principal, clinicId, appointmentId);
    }

    @PatchMapping("/doctor/appointments/{appointmentId}/status")
    public AppointmentDto updateStatus(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID appointmentId,
            @RequestParam UUID clinicId,
            @Valid @RequestBody StatusRequest request) {
        return appointments.updateStatus(principal, clinicId, appointmentId, request);
    }

    @PutMapping("/doctor/appointments/{appointmentId}/reschedule")
    public AppointmentDto doctorReschedule(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID appointmentId,
            @RequestParam UUID clinicId,
            @Valid @RequestBody RescheduleRequest request) {
        return appointments.rescheduleByDoctor(principal, clinicId, appointmentId, request);
    }

    @GetMapping("/doctor/schedules")
    public List<ScheduleDto> doctorSchedules(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId) {
        return appointments.doctorSchedules(principal, clinicId);
    }

    @PostMapping("/doctor/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleDto createSchedule(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @Valid @RequestBody ScheduleRequest request) {
        return appointments.createSchedule(principal, request);
    }

    @DeleteMapping("/doctor/schedules/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateSchedule(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID scheduleId,
            @RequestParam UUID clinicId) {
        appointments.deactivateSchedule(principal, clinicId, scheduleId);
    }

    @GetMapping("/doctor/schedule-exceptions")
    public List<ScheduleExceptionDto> scheduleExceptions(
            @AuthenticationPrincipal FirebasePrincipal principal, @RequestParam UUID clinicId) {
        return appointments.scheduleExceptions(principal, clinicId);
    }

    @PostMapping("/doctor/schedule-exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleExceptionDto createScheduleException(
            @AuthenticationPrincipal FirebasePrincipal principal, @Valid @RequestBody ScheduleExceptionRequest request) {
        return appointments.createScheduleException(principal, request);
    }

    @DeleteMapping("/doctor/schedule-exceptions/{exceptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeScheduleException(@AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID exceptionId, @RequestParam UUID clinicId) {
        appointments.removeScheduleException(principal, clinicId, exceptionId);
    }
}
