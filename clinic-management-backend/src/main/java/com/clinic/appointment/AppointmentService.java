package com.clinic.appointment;

import static com.clinic.appointment.AppointmentDtos.*;

import com.clinic.security.FirebasePrincipal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppointmentService {
    private static final RowMapper<AppointmentDto> APPOINTMENT_ROW = (rs, row) -> new AppointmentDto(
            rs.getObject("id", UUID.class),
            rs.getObject("clinic_id", UUID.class),
            rs.getObject("clinic_patient_id", UUID.class),
            rs.getString("clinic_name"),
            rs.getObject("doctor_profile_id", UUID.class),
            rs.getString("doctor_name"),
            rs.getString("specialization"),
            rs.getString("patient_code"),
            rs.getString("patient_name"),
            rs.getString("patient_email"),
            rs.getObject("starts_at", OffsetDateTime.class),
            rs.getObject("ends_at", OffsetDateTime.class),
            rs.getString("status"),
            rs.getString("reason"),
            rs.getString("cancellation_reason"),
            rs.getObject("created_at", OffsetDateTime.class));

    private static final RowMapper<SlotRecord> SLOT_ROW = (rs, row) -> new SlotRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("clinic_id", UUID.class),
            rs.getObject("doctor_profile_id", UUID.class),
            rs.getObject("starts_at", OffsetDateTime.class),
            rs.getObject("ends_at", OffsetDateTime.class),
            rs.getString("status"));

    private static final RowMapper<AppointmentRecord> APPOINTMENT_RECORD_ROW = (rs, row) -> new AppointmentRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("clinic_id", UUID.class),
            rs.getObject("clinic_patient_id", UUID.class),
            rs.getObject("doctor_profile_id", UUID.class),
            rs.getObject("slot_id", UUID.class),
            rs.getObject("starts_at", OffsetDateTime.class),
            rs.getObject("ends_at", OffsetDateTime.class),
            rs.getString("status"));

    private static final String APPOINTMENT_SELECT = """
            SELECT a.id, a.clinic_id, a.clinic_patient_id, c.name AS clinic_name,
                   a.doctor_profile_id, du.full_name AS doctor_name, dp.specialization,
                   cp.patient_code, COALESCE(fm.full_name, pp.preferred_name, pu.full_name) AS patient_name,
                   pu.email AS patient_email, a.starts_at, a.ends_at, a.status,
                   a.reason, a.cancellation_reason, a.created_at
            FROM appointments a
            JOIN clinics c ON c.id = a.clinic_id
            JOIN doctor_profiles dp ON dp.id = a.doctor_profile_id AND dp.clinic_id = a.clinic_id
            JOIN clinic_members dcm ON dcm.id = dp.clinic_member_id
            JOIN users du ON du.id = dcm.user_id
            JOIN clinic_patients cp ON cp.id = a.clinic_patient_id AND cp.clinic_id = a.clinic_id
            JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
            JOIN users pu ON pu.id = pp.user_id
            LEFT JOIN family_members fm ON fm.id = a.family_member_id AND fm.clinic_id = a.clinic_id AND fm.clinic_patient_id = cp.id
            """;

    private final JdbcTemplate jdbc;

    public AppointmentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ClinicDto> clinics() {
        return jdbc.query("""
                SELECT id, name, city, region, timezone
                FROM clinics WHERE active = true ORDER BY name
                """, (rs, row) -> new ClinicDto(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("city"),
                rs.getString("region"), rs.getString("timezone")));
    }

    @Transactional(readOnly = true)
    public List<DoctorDto> doctors(UUID clinicId) {
        return jdbc.query("""
                SELECT c.id AS clinic_id, c.name AS clinic_name, dp.id AS doctor_profile_id,
                       u.full_name, dp.specialization, dp.qualification,
                       dp.consultation_fee, dp.consultation_duration_minutes
                FROM clinics c
                JOIN clinic_members cm ON cm.clinic_id = c.id AND cm.role = 'DOCTOR' AND cm.active = true
                JOIN doctor_profiles dp ON dp.clinic_member_id = cm.id AND dp.clinic_id = c.id AND dp.active = true
                JOIN users u ON u.id = cm.user_id AND u.status = 'ACTIVE'
                WHERE c.id = ? AND c.active = true
                ORDER BY u.full_name
                """, (rs, row) -> new DoctorDto(
                rs.getObject("clinic_id", UUID.class), rs.getString("clinic_name"),
                rs.getObject("doctor_profile_id", UUID.class), rs.getString("full_name"),
                rs.getString("specialization"), rs.getString("qualification"),
                rs.getBigDecimal("consultation_fee"), rs.getInt("consultation_duration_minutes")), clinicId);
    }

    @Transactional
    public List<SlotDto> availableSlots(UUID clinicId, UUID doctorProfileId, LocalDate date) {
        ZoneId zone = clinicZone(clinicId);
        LocalDate today = LocalDate.now(zone);
        if (date.isBefore(today)) throw badRequest("Choose today or a future date");
        if (date.isAfter(today.plusDays(bookingWindowDays(clinicId)))) return List.of();
        ensureActiveDoctor(clinicId, doctorProfileId);
        Integer closed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM doctor_schedule_exceptions
                WHERE clinic_id = ? AND doctor_profile_id = ? AND exception_date = ? AND active = true
                """, Integer.class, clinicId, doctorProfileId, date);
        if (closed != null && closed > 0) return List.of();

        int weekday = date.getDayOfWeek().getValue() % 7;
        List<ScheduleWindow> schedules = jdbc.query("""
                SELECT id, starts_at, ends_at, slot_duration_minutes
                FROM doctor_schedules
                WHERE clinic_id = ? AND doctor_profile_id = ? AND weekday = ? AND active = true
                  AND effective_from <= ? AND (effective_until IS NULL OR effective_until >= ?)
                ORDER BY starts_at
                """, (rs, row) -> new ScheduleWindow(
                rs.getObject("id", UUID.class),
                rs.getObject("starts_at", LocalTime.class),
                rs.getObject("ends_at", LocalTime.class),
                rs.getInt("slot_duration_minutes")), clinicId, doctorProfileId, weekday, date, date);

        OffsetDateTime now = OffsetDateTime.now(zone);
        for (ScheduleWindow schedule : schedules) {
            List<ScheduleBreakDto> breaks = scheduleBreaks(schedule.id());
            LocalTime startsAt = schedule.startsAt();
            while (!startsAt.plusMinutes(schedule.durationMinutes()).isAfter(schedule.endsAt())) {
                LocalTime slotStart = startsAt;
                LocalTime endsAt = startsAt.plusMinutes(schedule.durationMinutes());
                boolean overlapsBreak = breaks.stream().anyMatch(item ->
                        slotStart.isBefore(item.endsAt()) && endsAt.isAfter(item.startsAt()));
                OffsetDateTime startInstant = date.atTime(startsAt).atZone(zone).toOffsetDateTime();
                OffsetDateTime endInstant = date.atTime(endsAt).atZone(zone).toOffsetDateTime();
                if (!overlapsBreak && startInstant.isAfter(now)) {
                    jdbc.update("""
                            INSERT INTO appointment_slots
                                (clinic_id, doctor_profile_id, schedule_id, starts_at, ends_at, status)
                            VALUES (?, ?, ?, ?, ?, 'AVAILABLE')
                            ON CONFLICT DO NOTHING
                            """, clinicId, doctorProfileId, schedule.id(), startInstant, endInstant);
                }
                startsAt = endsAt;
            }
        }

        OffsetDateTime dayStart = date.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        return jdbc.query("""
                SELECT id, starts_at, ends_at
                FROM appointment_slots
                WHERE clinic_id = ? AND doctor_profile_id = ? AND status = 'AVAILABLE'
                  AND starts_at >= ? AND starts_at < ? AND starts_at > ?
                ORDER BY starts_at
                """, (rs, row) -> new SlotDto(
                rs.getObject("id", UUID.class),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class)),
                clinicId, doctorProfileId, dayStart, dayEnd, now);
    }

    @Transactional
    public AppointmentDto book(FirebasePrincipal principal, BookingRequest request) {
        UUID userId = userId(principal);
        UUID patientProfileId = patientProfileId(userId);
        ensureActiveDoctor(request.clinicId(), request.doctorProfileId());
        UUID clinicPatientId = ensureClinicPatient(request.clinicId(), patientProfileId);
        validateFamilyMember(request.clinicId(), clinicPatientId, request.familyMemberId());
        SlotRecord slot = lockSlot(request.clinicId(), request.doctorProfileId(), request.slotId());
        if (!"AVAILABLE".equals(slot.status()) || !slot.startsAt().isAfter(OffsetDateTime.now())) {
            throw conflict("That appointment time is no longer available");
        }
        if (slot.startsAt().isAfter(OffsetDateTime.now().plusDays(bookingWindowDays(request.clinicId())))) {
            throw badRequest("That appointment is outside the clinic booking window");
        }
        if (jdbc.update("UPDATE appointment_slots SET status = 'BOOKED', hold_expires_at = NULL WHERE id = ? AND status = 'AVAILABLE'",
                slot.id()) != 1) {
            throw conflict("That appointment time was just booked by someone else");
        }

        UUID appointmentId = jdbc.queryForObject("""
                INSERT INTO appointments
                    (clinic_id, clinic_patient_id, doctor_profile_id, slot_id, family_member_id, booked_by_user_id,
                     starts_at, ends_at, status, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                RETURNING id
                """, UUID.class, request.clinicId(), clinicPatientId, request.doctorProfileId(),
                slot.id(), request.familyMemberId(), userId, slot.startsAt(), slot.endsAt(), clean(request.reason()));
        addStatusHistory(request.clinicId(), appointmentId, null, "PENDING", userId, "Booked by patient");
        notifyUser(request.clinicId(), userId, "APPOINTMENT", "Appointment request received",
                "Your appointment request for " + slot.startsAt() + " is waiting for clinic confirmation.");
        notifyUser(request.clinicId(), doctorUserId(request.doctorProfileId()), "APPOINTMENT", "New appointment request",
                "A patient requested an appointment for " + slot.startsAt() + ".");
        return patientAppointment(principal, appointmentId);
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> patientAppointments(FirebasePrincipal principal) {
        UUID userId = userId(principal);
        patientProfileId(userId);
        return jdbc.query(APPOINTMENT_SELECT + """
                WHERE pp.user_id = ? ORDER BY a.starts_at DESC LIMIT 500
                """, APPOINTMENT_ROW, userId);
    }

    @Transactional(readOnly = true)
    public AppointmentDto patientAppointment(FirebasePrincipal principal, UUID appointmentId) {
        UUID userId = userId(principal);
        patientProfileId(userId);
        return oneAppointment(APPOINTMENT_SELECT + " WHERE a.id = ? AND pp.user_id = ?", appointmentId, userId);
    }

    @Transactional
    public AppointmentDto updateReason(FirebasePrincipal principal, UUID appointmentId, UpdateAppointmentRequest request) {
        UUID userId = userId(principal);
        UUID patientProfileId = patientProfileId(userId);
        AppointmentRecord current = lockPatientAppointment(appointmentId, userId);
        if (!"PENDING".equals(current.status()) && !"CONFIRMED".equals(current.status())) {
            throw conflict("This appointment can no longer be edited");
        }
        if (!current.startsAt().isAfter(OffsetDateTime.now())) throw conflict("Past appointments cannot be edited");
        jdbc.update("UPDATE appointments SET reason = ?, updated_at = now() WHERE id = ?",
                clean(request.reason()), appointmentId);
        return patientAppointment(principal, appointmentId);
    }

    @Transactional
    public AppointmentDto cancelByPatient(FirebasePrincipal principal, UUID appointmentId, CancelRequest request) {
        UUID userId = userId(principal);
        patientProfileId(userId);
        AppointmentRecord current = lockPatientAppointment(appointmentId, userId);
        if (!"PENDING".equals(current.status()) && !"CONFIRMED".equals(current.status())) {
            throw conflict("Only pending or confirmed future appointments can be cancelled");
        }
        if (!current.startsAt().isAfter(OffsetDateTime.now())) throw conflict("Past appointments cannot be cancelled");
        enforcePatientChangeWindow(current);
        cancelAppointment(current, userId, clean(request == null ? null : request.reason()), "CANCELLED");
        notifyUser(current.clinicId(), doctorUserId(current.doctorProfileId()), "APPOINTMENT", "Patient cancelled an appointment",
                "An appointment scheduled for " + current.startsAt() + " was cancelled by the patient.");
        return patientAppointment(principal, appointmentId);
    }

    @Transactional
    public AppointmentDto rescheduleByPatient(FirebasePrincipal principal, UUID appointmentId, RescheduleRequest request) {
        UUID userId = userId(principal);
        patientProfileId(userId);
        AppointmentRecord current = lockPatientAppointment(appointmentId, userId);
        requireReschedulable(current);
        enforcePatientChangeWindow(current);
        return reschedule(principal, userId, current, request.slotId(), "Rescheduled by patient");
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> doctorAppointments(
            FirebasePrincipal principal, UUID clinicId, LocalDate from, LocalDate to) {
        UUID userId = userId(principal);
        UUID doctorProfileId = doctorProfileId(userId, clinicId);
        ZoneId zone = clinicZone(clinicId);
        LocalDate today = LocalDate.now(zone);
        LocalDate start = from == null ? today.minusDays(30) : from;
        LocalDate finish = to == null ? today.plusDays(90) : to;
        if (finish.isBefore(start) || Duration.between(start.atStartOfDay(), finish.plusDays(1).atStartOfDay()).toDays() > 366) {
            throw badRequest("Appointment date range must be valid and no longer than one year");
        }
        OffsetDateTime startAt = start.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime endAt = finish.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        return jdbc.query(APPOINTMENT_SELECT + """
                WHERE a.clinic_id = ? AND a.doctor_profile_id = ?
                  AND a.starts_at >= ? AND a.starts_at < ?
                ORDER BY a.starts_at LIMIT 1000
                """, APPOINTMENT_ROW, clinicId, doctorProfileId, startAt, endAt);
    }

    @Transactional(readOnly = true)
    public AppointmentDto doctorAppointment(FirebasePrincipal principal, UUID clinicId, UUID appointmentId) {
        UUID doctorProfileId = doctorProfileId(userId(principal), clinicId);
        return oneAppointment(APPOINTMENT_SELECT + " WHERE a.id = ? AND a.clinic_id = ? AND a.doctor_profile_id = ?",
                appointmentId, clinicId, doctorProfileId);
    }

    @Transactional
    public AppointmentDto updateStatus(
            FirebasePrincipal principal, UUID clinicId, UUID appointmentId, StatusRequest request) {
        UUID userId = userId(principal);
        UUID doctorProfileId = doctorProfileId(userId, clinicId);
        AppointmentRecord current = lockDoctorAppointment(appointmentId, clinicId, doctorProfileId);
        String next = request.status();
        if (!allowedTransition(current.status(), next)) {
            throw conflict("Appointment status cannot change from " + current.status() + " to " + next);
        }
        String reason = clean(request.reason());
        jdbc.update("""
                UPDATE appointments
                SET status = ?, cancellation_reason = ?, updated_at = now()
                WHERE id = ? AND clinic_id = ?
                """, next, isReleased(next) ? reason : null, appointmentId, clinicId);
        if (isReleased(next)) releaseSlot(current.slotId());
        addStatusHistory(clinicId, appointmentId, current.status(), next, userId, reason);
        notifyUser(clinicId, patientUserId(current.clinicPatientId()), "APPOINTMENT", "Appointment " + next.toLowerCase().replace('_', ' '),
                "Your appointment scheduled for " + current.startsAt() + " was updated to " + next.toLowerCase().replace('_', ' ') + ".");
        return doctorAppointment(principal, clinicId, appointmentId);
    }

    @Transactional
    public AppointmentDto rescheduleByDoctor(
            FirebasePrincipal principal, UUID clinicId, UUID appointmentId, RescheduleRequest request) {
        UUID userId = userId(principal);
        UUID doctorProfileId = doctorProfileId(userId, clinicId);
        AppointmentRecord current = lockDoctorAppointment(appointmentId, clinicId, doctorProfileId);
        requireReschedulable(current);
        return reschedule(principal, userId, current, request.slotId(), "Rescheduled by doctor");
    }

    @Transactional(readOnly = true)
    public DashboardDto doctorDashboard(FirebasePrincipal principal, UUID clinicId) {
        UUID doctorProfileId = doctorProfileId(userId(principal), clinicId);
        ZoneId zone = clinicZone(clinicId);
        LocalDate today = LocalDate.now(zone);
        OffsetDateTime start = today.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = today.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        return jdbc.queryForObject("""
                SELECT COUNT(DISTINCT a.clinic_patient_id) AS total_patients,
                       COUNT(*) FILTER (WHERE a.starts_at >= ? AND a.starts_at < ?) AS today_appointments,
                       COUNT(*) FILTER (WHERE a.status = 'PENDING') AS pending_appointments,
                       COUNT(*) FILTER (WHERE a.status = 'COMPLETED') AS completed_appointments,
                       COUNT(*) FILTER (WHERE a.status = 'CANCELLED') AS cancelled_appointments
                FROM appointments a
                WHERE a.clinic_id = ? AND a.doctor_profile_id = ?
                """, (rs, row) -> new DashboardDto(
                rs.getLong("total_patients"), rs.getLong("today_appointments"),
                rs.getLong("pending_appointments"), rs.getLong("completed_appointments"),
                rs.getLong("cancelled_appointments")), start, end, clinicId, doctorProfileId);
    }

    @Transactional(readOnly = true)
    public List<ScheduleDto> doctorSchedules(FirebasePrincipal principal, UUID clinicId) {
        UUID doctorProfileId = doctorProfileId(userId(principal), clinicId);
        List<UUID> ids = jdbc.query("""
                SELECT id FROM doctor_schedules
                WHERE clinic_id = ? AND doctor_profile_id = ?
                ORDER BY weekday, starts_at
                """, (rs, row) -> rs.getObject("id", UUID.class), clinicId, doctorProfileId);
        return ids.stream().map(this::schedule).toList();
    }

    @Transactional
    public ScheduleDto createSchedule(FirebasePrincipal principal, ScheduleRequest request) {
        UUID userId = userId(principal);
        UUID doctorProfileId = doctorProfileId(userId, request.clinicId());
        validateSchedule(request);
        LocalDate effectiveUntil = request.effectiveUntil() == null
                ? LocalDate.of(9999, 12, 31) : request.effectiveUntil();
        Integer overlaps = jdbc.queryForObject("""
                SELECT COUNT(*) FROM doctor_schedules
                WHERE clinic_id = ? AND doctor_profile_id = ? AND weekday = ? AND active = true
                  AND (effective_until IS NULL OR effective_until >= ?)
                  AND effective_from <= ? AND starts_at < ? AND ends_at > ?
                """, Integer.class, request.clinicId(), doctorProfileId, request.weekday(),
                request.effectiveFrom(), effectiveUntil, request.endsAt(), request.startsAt());
        if (overlaps != null && overlaps > 0) throw conflict("This schedule overlaps an active schedule");

        UUID scheduleId = jdbc.queryForObject("""
                INSERT INTO doctor_schedules
                    (clinic_id, doctor_profile_id, weekday, starts_at, ends_at, slot_duration_minutes,
                     effective_from, effective_until)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class, request.clinicId(), doctorProfileId, request.weekday(), request.startsAt(),
                request.endsAt(), request.slotDurationMinutes(), request.effectiveFrom(), request.effectiveUntil());
        List<ScheduleBreakRequest> breaks = request.breaks() == null ? List.of() : request.breaks();
        for (ScheduleBreakRequest item : breaks) {
            jdbc.update("""
                    INSERT INTO schedule_breaks (schedule_id, starts_at, ends_at, label)
                    VALUES (?, ?, ?, ?)
                    """, scheduleId, item.startsAt(), item.endsAt(), clean(item.label()));
        }
        return schedule(scheduleId);
    }

    @Transactional
    public void deactivateSchedule(FirebasePrincipal principal, UUID clinicId, UUID scheduleId) {
        UUID doctorProfileId = doctorProfileId(userId(principal), clinicId);
        int changed = jdbc.update("""
                UPDATE doctor_schedules SET active = false
                WHERE id = ? AND clinic_id = ? AND doctor_profile_id = ? AND active = true
                """, scheduleId, clinicId, doctorProfileId);
        if (changed != 1) throw notFound("Schedule not found");
        jdbc.update("""
                UPDATE appointment_slots s SET status = 'BLOCKED'
                WHERE s.schedule_id = ? AND s.status = 'AVAILABLE' AND s.starts_at > now()
                """, scheduleId);
    }

    @Transactional(readOnly = true)
    public List<ScheduleExceptionDto> scheduleExceptions(FirebasePrincipal principal, UUID clinicId) {
        UUID doctorId = doctorProfileId(userId(principal), clinicId);
        return jdbc.query("""
                SELECT id, clinic_id, exception_date, reason
                FROM doctor_schedule_exceptions
                WHERE clinic_id = ? AND doctor_profile_id = ? AND active = true AND exception_date >= CURRENT_DATE
                ORDER BY exception_date LIMIT 200
                """, (rs, row) -> new ScheduleExceptionDto(rs.getObject("id", UUID.class),
                rs.getObject("clinic_id", UUID.class), rs.getObject("exception_date", LocalDate.class), rs.getString("reason")),
                clinicId, doctorId);
    }

    @Transactional
    public ScheduleExceptionDto createScheduleException(FirebasePrincipal principal, ScheduleExceptionRequest request) {
        UUID userId = userId(principal);
        UUID doctorId = doctorProfileId(userId, request.clinicId());
        ZoneId zone = clinicZone(request.clinicId());
        if (request.exceptionDate().isBefore(LocalDate.now(zone))) throw badRequest("A schedule exception cannot be created in the past");
        UUID id;
        try {
            id = jdbc.queryForObject("""
                    INSERT INTO doctor_schedule_exceptions
                        (clinic_id, doctor_profile_id, exception_date, reason, created_by_user_id, active)
                    VALUES (?, ?, ?, ?, ?, true)
                    ON CONFLICT (clinic_id, doctor_profile_id, exception_date)
                    DO UPDATE SET reason = EXCLUDED.reason, created_by_user_id = EXCLUDED.created_by_user_id, active = true
                    WHERE doctor_schedule_exceptions.active = false
                    RETURNING id
                    """, UUID.class, request.clinicId(), doctorId, request.exceptionDate(), clean(request.reason()), userId);
        } catch (EmptyResultDataAccessException exception) {
            throw conflict("A schedule exception already exists for that day");
        }
        OffsetDateTime start = request.exceptionDate().atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = request.exceptionDate().plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        jdbc.update("""
                UPDATE appointment_slots SET status = 'BLOCKED'
                WHERE clinic_id = ? AND doctor_profile_id = ? AND starts_at >= ? AND starts_at < ? AND status = 'AVAILABLE'
                """, request.clinicId(), doctorId, start, end);
        audit(request.clinicId(), userId, "SCHEDULE_EXCEPTION_CREATED", id, "date", request.exceptionDate().toString());
        return scheduleException(request.clinicId(), id, doctorId);
    }

    @Transactional
    public void removeScheduleException(FirebasePrincipal principal, UUID clinicId, UUID exceptionId) {
        UUID userId = userId(principal);
        UUID doctorId = doctorProfileId(userId, clinicId);
        LocalDate date = jdbc.query("""
                UPDATE doctor_schedule_exceptions SET active = false
                WHERE id = ? AND clinic_id = ? AND doctor_profile_id = ? AND active = true
                RETURNING exception_date
                """, (rs, row) -> rs.getObject("exception_date", LocalDate.class), exceptionId, clinicId, doctorId)
                .stream().findFirst().orElseThrow(() -> notFound("Schedule exception not found"));
        ZoneId zone = clinicZone(clinicId);
        OffsetDateTime start = date.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        jdbc.update("""
                UPDATE appointment_slots s SET status = 'AVAILABLE'
                FROM doctor_schedules ds
                WHERE s.schedule_id = ds.id AND ds.active = true AND s.clinic_id = ? AND s.doctor_profile_id = ?
                  AND s.starts_at >= ? AND s.starts_at < ? AND s.starts_at > now() AND s.status = 'BLOCKED'
                  AND NOT EXISTS (
                      SELECT 1 FROM appointments a WHERE a.slot_id = s.id
                        AND a.status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'IN_CONSULTATION')
                  )
                """, clinicId, doctorId, start, end);
        audit(clinicId, userId, "SCHEDULE_EXCEPTION_REMOVED", exceptionId, "date", date.toString());
    }

    private ScheduleExceptionDto scheduleException(UUID clinicId, UUID exceptionId, UUID doctorId) {
        return jdbc.query("""
                SELECT id, clinic_id, exception_date, reason
                FROM doctor_schedule_exceptions WHERE id = ? AND clinic_id = ? AND doctor_profile_id = ?
                """, (rs, row) -> new ScheduleExceptionDto(rs.getObject("id", UUID.class),
                rs.getObject("clinic_id", UUID.class), rs.getObject("exception_date", LocalDate.class), rs.getString("reason")),
                exceptionId, clinicId, doctorId).stream().findFirst().orElseThrow(() -> notFound("Schedule exception not found"));
    }

    private void audit(UUID clinicId, UUID actorId, String action, UUID entityId, String key, String value) {
        jdbc.update("""
                INSERT INTO audit_logs (clinic_id, actor_user_id, action, entity_type, entity_id, after_data)
                VALUES (?, ?, ?, 'schedule', ?, jsonb_build_object(?, ?))
                """, clinicId, actorId, action, entityId, key, value);
    }

    private AppointmentDto reschedule(
            FirebasePrincipal principal, UUID actorId, AppointmentRecord current, UUID newSlotId, String historyReason) {
        if (current.slotId().equals(newSlotId)) throw badRequest("Choose a different appointment time");
        List<UUID> ids = List.of(current.slotId(), newSlotId).stream().sorted(Comparator.naturalOrder()).toList();
        List<SlotRecord> lockedSlots = jdbc.query("""
                SELECT id, clinic_id, doctor_profile_id, starts_at, ends_at, status
                FROM appointment_slots WHERE id IN (?, ?) ORDER BY id FOR UPDATE
                """, SLOT_ROW, ids.get(0), ids.get(1));
        SlotRecord oldSlot = lockedSlots.stream().filter(item -> item.id().equals(current.slotId())).findFirst()
                .orElseThrow(() -> notFound("Appointment slot not found"));
        SlotRecord newSlot = lockedSlots.stream().filter(item -> item.id().equals(newSlotId)).findFirst()
                .orElseThrow(() -> notFound("New appointment time not found"));
        if (!newSlot.clinicId().equals(current.clinicId()) || !newSlot.doctorProfileId().equals(current.doctorProfileId())) {
            throw badRequest("Rescheduling must use an available time with the same doctor");
        }
        if (newSlot.startsAt().isAfter(OffsetDateTime.now().plusDays(bookingWindowDays(current.clinicId())))) {
            throw badRequest("That appointment is outside the clinic booking window");
        }
        if (!"AVAILABLE".equals(newSlot.status()) || !newSlot.startsAt().isAfter(OffsetDateTime.now())) {
            throw conflict("That appointment time is no longer available");
        }
        if (jdbc.update("UPDATE appointment_slots SET status = 'BOOKED' WHERE id = ? AND status = 'AVAILABLE'",
                newSlot.id()) != 1) throw conflict("That appointment time was just booked by someone else");
        jdbc.update("""
                UPDATE appointments
                SET slot_id = ?, starts_at = ?, ends_at = ?, status = 'PENDING',
                    cancellation_reason = NULL, updated_at = now()
                WHERE id = ?
                """, newSlot.id(), newSlot.startsAt(), newSlot.endsAt(), current.id());
        if ("BOOKED".equals(oldSlot.status())) {
            jdbc.update("UPDATE appointment_slots SET status = 'AVAILABLE' WHERE id = ? AND status = 'BOOKED'", oldSlot.id());
        }
        addStatusHistory(current.clinicId(), current.id(), current.status(), "PENDING", actorId, historyReason);
        notifyUser(current.clinicId(), patientUserId(current.clinicPatientId()), "APPOINTMENT", "Appointment rescheduled",
                "Your appointment time changed to " + newSlot.startsAt() + " and is waiting for clinic confirmation.");
        if (isPatientAppointment(principal, current.id())) return patientAppointment(principal, current.id());
        return doctorAppointment(principal, current.clinicId(), current.id());
    }

    private boolean isPatientAppointment(FirebasePrincipal principal, UUID appointmentId) {
        UUID userId = userId(principal);
        Integer owned = jdbc.queryForObject("""
                SELECT COUNT(*) FROM appointments a
                JOIN clinic_patients cp ON cp.id = a.clinic_patient_id
                JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                WHERE a.id = ? AND pp.user_id = ?
                """, Integer.class, appointmentId, userId);
        return owned != null && owned > 0;
    }

    private void requireReschedulable(AppointmentRecord current) {
        if (!"PENDING".equals(current.status()) && !"CONFIRMED".equals(current.status())) {
            throw conflict("Only pending or confirmed appointments can be rescheduled");
        }
        if (!current.startsAt().isAfter(OffsetDateTime.now())) throw conflict("Past appointments cannot be rescheduled");
    }

    private void cancelAppointment(AppointmentRecord current, UUID userId, String reason, String status) {
        jdbc.update("""
                UPDATE appointments
                SET status = ?, cancellation_reason = ?, updated_at = now()
                WHERE id = ?
                """, status, reason, current.id());
        releaseSlot(current.slotId());
        addStatusHistory(current.clinicId(), current.id(), current.status(), status, userId, reason);
    }

    private void releaseSlot(UUID slotId) {
        jdbc.update("UPDATE appointment_slots SET status = 'AVAILABLE', hold_expires_at = NULL WHERE id = ? AND status = 'BOOKED'",
                slotId);
    }

    private void addStatusHistory(
            UUID clinicId, UUID appointmentId, String previous, String next, UUID userId, String reason) {
        jdbc.update("""
                INSERT INTO appointment_status_history
                    (clinic_id, appointment_id, previous_status, new_status, changed_by_user_id, reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """, clinicId, appointmentId, previous, next, userId, reason);
    }

    private AppointmentRecord lockPatientAppointment(UUID appointmentId, UUID userId) {
        return jdbc.query("""
                SELECT a.id, a.clinic_id, a.clinic_patient_id, a.doctor_profile_id, a.slot_id,
                       a.starts_at, a.ends_at, a.status
                FROM appointments a
                JOIN clinic_patients cp ON cp.id = a.clinic_patient_id
                JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                WHERE a.id = ? AND pp.user_id = ?
                FOR UPDATE OF a
                """, APPOINTMENT_RECORD_ROW, appointmentId, userId).stream().findFirst()
                .orElseThrow(() -> notFound("Appointment not found"));
    }

    private AppointmentRecord lockDoctorAppointment(UUID appointmentId, UUID clinicId, UUID doctorProfileId) {
        return jdbc.query("""
                SELECT id, clinic_id, clinic_patient_id, doctor_profile_id, slot_id,
                       starts_at, ends_at, status
                FROM appointments
                WHERE id = ? AND clinic_id = ? AND doctor_profile_id = ?
                FOR UPDATE
                """, APPOINTMENT_RECORD_ROW, appointmentId, clinicId, doctorProfileId).stream().findFirst()
                .orElseThrow(() -> notFound("Appointment not found"));
    }

    private SlotRecord lockSlot(UUID clinicId, UUID doctorProfileId, UUID slotId) {
        return jdbc.query("""
                SELECT s.id, s.clinic_id, s.doctor_profile_id, s.starts_at, s.ends_at, s.status
                FROM appointment_slots s
                JOIN doctor_profiles dp ON dp.id = s.doctor_profile_id AND dp.clinic_id = s.clinic_id AND dp.active = true
                JOIN clinics c ON c.id = s.clinic_id AND c.active = true
                WHERE s.id = ? AND s.clinic_id = ? AND s.doctor_profile_id = ?
                FOR UPDATE OF s
                """, SLOT_ROW, slotId, clinicId, doctorProfileId).stream().findFirst()
                .orElseThrow(() -> notFound("Available appointment time not found"));
    }

    private UUID ensureClinicPatient(UUID clinicId, UUID patientProfileId) {
        String patientCode = "P-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return jdbc.queryForObject("""
                INSERT INTO clinic_patients (clinic_id, patient_profile_id, patient_code)
                VALUES (?, ?, ?)
                ON CONFLICT (clinic_id, patient_profile_id)
                DO UPDATE SET patient_profile_id = EXCLUDED.patient_profile_id
                RETURNING id
                """, UUID.class, clinicId, patientProfileId, patientCode);
    }

    private UUID patientUserId(UUID clinicPatientId) {
        return jdbc.queryForObject("""
                SELECT pp.user_id FROM clinic_patients cp JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                WHERE cp.id = ?
                """, UUID.class, clinicPatientId);
    }

    private UUID doctorUserId(UUID doctorProfileId) {
        return jdbc.queryForObject("""
                SELECT cm.user_id FROM doctor_profiles dp JOIN clinic_members cm ON cm.id = dp.clinic_member_id
                WHERE dp.id = ?
                """, UUID.class, doctorProfileId);
    }

    private void notifyUser(UUID clinicId, UUID userId, String category, String title, String body) {
        jdbc.update("""
                INSERT INTO notifications (clinic_id, user_id, category, title, body)
                VALUES (?, ?, ?, ?, ?)
                """, clinicId, userId, category, title, body);
    }

    private void validateFamilyMember(UUID clinicId, UUID clinicPatientId, UUID familyMemberId) {
        if (familyMemberId == null) return;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM family_members
                WHERE id = ? AND clinic_id = ? AND clinic_patient_id = ? AND authorized_by_patient = true
                """, Integer.class, familyMemberId, clinicId, clinicPatientId);
        if (count == null || count == 0) throw new AccessDeniedException("You are not authorized to book for this family member");
    }

    private UUID userId(FirebasePrincipal principal) {
        if (principal == null || principal.uid() == null || principal.uid().isBlank()) {
            throw new AccessDeniedException("Sign in is required");
        }
        try {
            return jdbc.queryForObject("SELECT id FROM users WHERE firebase_uid = ? AND status = 'ACTIVE'",
                    UUID.class, principal.uid());
        } catch (EmptyResultDataAccessException exception) {
            throw new AccessDeniedException("Active account not found");
        }
    }

    private UUID patientProfileId(UUID userId) {
        return jdbc.query("SELECT id FROM patient_profiles WHERE user_id = ?",
                (rs, row) -> rs.getObject("id", UUID.class), userId).stream().findFirst()
                .orElseThrow(() -> new AccessDeniedException("A patient account is required"));
    }

    private UUID doctorProfileId(UUID userId, UUID clinicId) {
        return jdbc.query("""
                SELECT dp.id
                FROM doctor_profiles dp
                JOIN clinic_members cm ON cm.id = dp.clinic_member_id AND cm.clinic_id = dp.clinic_id
                JOIN clinics c ON c.id = dp.clinic_id
                WHERE cm.user_id = ? AND dp.clinic_id = ? AND cm.role = 'DOCTOR'
                  AND cm.active = true AND dp.active = true AND c.active = true
                """, (rs, row) -> rs.getObject("id", UUID.class), userId, clinicId).stream().findFirst()
                .orElseThrow(() -> new AccessDeniedException("Doctor access is not enabled for this clinic"));
    }

    private void ensureActiveDoctor(UUID clinicId, UUID doctorProfileId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM doctor_profiles dp
                JOIN clinic_members cm ON cm.id = dp.clinic_member_id AND cm.clinic_id = dp.clinic_id
                JOIN clinics c ON c.id = dp.clinic_id
                WHERE dp.id = ? AND dp.clinic_id = ? AND dp.active = true
                  AND cm.role = 'DOCTOR' AND cm.active = true AND c.active = true
                """, Integer.class, doctorProfileId, clinicId);
        if (count == null || count == 0) throw notFound("Doctor not found");
    }

    private ZoneId clinicZone(UUID clinicId) {
        String timezone = jdbc.query("SELECT timezone FROM clinics WHERE id = ? AND active = true",
                (rs, row) -> rs.getString("timezone"), clinicId).stream().findFirst()
                .orElseThrow(() -> notFound("Clinic not found"));
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Clinic timezone is invalid");
        }
    }

    private int bookingWindowDays(UUID clinicId) {
        Integer days = jdbc.queryForObject("""
                SELECT COALESCE((SELECT (setting_value->>'bookingWindowDays')::integer
                                 FROM clinic_settings WHERE clinic_id = ? AND setting_key = 'appointment_policy'), 90)
                """, Integer.class, clinicId);
        return days == null ? 90 : days;
    }

    private void enforcePatientChangeWindow(AppointmentRecord appointment) {
        Integer hours = jdbc.queryForObject("""
                SELECT COALESCE((SELECT (setting_value->>'cancellationHours')::integer
                                 FROM clinic_settings WHERE clinic_id = ? AND setting_key = 'appointment_policy'), 24)
                """, Integer.class, appointment.clinicId());
        int cancellationHours = hours == null ? 24 : hours;
        if (appointment.startsAt().isBefore(OffsetDateTime.now().plusHours(cancellationHours))) {
            throw conflict("This clinic requires changes at least " + cancellationHours + " hours before the appointment");
        }
    }

    private void validateSchedule(ScheduleRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) throw badRequest("Schedule end must be after its start");
        if (request.effectiveUntil() != null && request.effectiveUntil().isBefore(request.effectiveFrom())) {
            throw badRequest("Schedule end date must not be before its start date");
        }
        if (request.effectiveFrom().isBefore(LocalDate.now(clinicZone(request.clinicId())))) {
            throw badRequest("Schedule start date cannot be in the past");
        }
        if (Duration.between(request.startsAt(), request.endsAt()).toMinutes() < request.slotDurationMinutes()) {
            throw badRequest("Schedule must be at least one slot long");
        }
        List<ScheduleBreakRequest> breaks = request.breaks() == null ? List.of() : request.breaks();
        List<ScheduleBreakRequest> sorted = new ArrayList<>(breaks);
        sorted.sort(Comparator.comparing(ScheduleBreakRequest::startsAt));
        LocalTime previousEnd = request.startsAt();
        for (ScheduleBreakRequest item : sorted) {
            if (!item.endsAt().isAfter(item.startsAt())
                    || item.startsAt().isBefore(request.startsAt())
                    || item.endsAt().isAfter(request.endsAt())
                    || item.startsAt().isBefore(previousEnd)) {
                throw badRequest("Schedule breaks must be inside working hours and cannot overlap");
            }
            previousEnd = item.endsAt();
        }
    }

    private ScheduleDto schedule(UUID scheduleId) {
        ScheduleRow row = jdbc.query("""
                SELECT id, clinic_id, weekday, starts_at, ends_at, slot_duration_minutes,
                       effective_from, effective_until, active
                FROM doctor_schedules WHERE id = ?
                """, (rs, index) -> new ScheduleRow(
                rs.getObject("id", UUID.class), rs.getObject("clinic_id", UUID.class),
                rs.getInt("weekday"), rs.getObject("starts_at", LocalTime.class),
                rs.getObject("ends_at", LocalTime.class), rs.getInt("slot_duration_minutes"),
                rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_until", LocalDate.class),
                rs.getBoolean("active")), scheduleId).stream().findFirst()
                .orElseThrow(() -> notFound("Schedule not found"));
        return new ScheduleDto(row.id(), row.clinicId(), row.weekday(), row.startsAt(), row.endsAt(),
                row.durationMinutes(), row.effectiveFrom(), row.effectiveUntil(), row.active(), scheduleBreaks(row.id()));
    }

    private List<ScheduleBreakDto> scheduleBreaks(UUID scheduleId) {
        return jdbc.query("""
                SELECT starts_at, ends_at, label
                FROM schedule_breaks WHERE schedule_id = ? ORDER BY starts_at
                """, (rs, row) -> new ScheduleBreakDto(
                rs.getObject("starts_at", LocalTime.class),
                rs.getObject("ends_at", LocalTime.class), rs.getString("label")), scheduleId);
    }

    private AppointmentDto oneAppointment(String sql, Object... args) {
        return jdbc.query(sql, APPOINTMENT_ROW, args).stream().findFirst()
                .orElseThrow(() -> notFound("Appointment not found"));
    }

    private boolean allowedTransition(String current, String next) {
        return switch (current) {
            case "PENDING" -> List.of("CONFIRMED", "REJECTED", "CANCELLED").contains(next);
            case "CONFIRMED" -> List.of("CHECKED_IN", "CANCELLED", "NO_SHOW").contains(next);
            case "CHECKED_IN" -> "IN_CONSULTATION".equals(next);
            case "IN_CONSULTATION" -> "COMPLETED".equals(next);
            default -> false;
        };
    }

    private boolean isReleased(String status) {
        return "CANCELLED".equals(status) || "REJECTED".equals(status);
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private record SlotRecord(UUID id, UUID clinicId, UUID doctorProfileId, OffsetDateTime startsAt,
            OffsetDateTime endsAt, String status) {}
    private record AppointmentRecord(UUID id, UUID clinicId, UUID clinicPatientId, UUID doctorProfileId,
            UUID slotId, OffsetDateTime startsAt, OffsetDateTime endsAt, String status) {}
    private record ScheduleWindow(UUID id, LocalTime startsAt, LocalTime endsAt, int durationMinutes) {}
    private record ScheduleRow(UUID id, UUID clinicId, int weekday, LocalTime startsAt, LocalTime endsAt,
            int durationMinutes, LocalDate effectiveFrom, LocalDate effectiveUntil, boolean active) {}
}
