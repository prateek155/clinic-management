package com.clinic.doctor;

import static com.clinic.doctor.DoctorSettingsDtos.*;

import com.clinic.security.FirebasePrincipal;
import java.time.ZoneId;
import java.util.UUID;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DoctorSettingsService {
    private final JdbcTemplate jdbc;

    public DoctorSettingsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public SettingsDto get(FirebasePrincipal principal, UUID clinicId) {
        UUID userId = userId(principal);
        return row(doctorProfileId(userId, clinicId), clinicId);
    }

    @Transactional
    public SettingsDto update(FirebasePrincipal principal, UUID clinicId, SettingsRequest request) {
        UUID userId = userId(principal);
        UUID doctorId = doctorProfileId(userId, clinicId);
        try { ZoneId.of(request.timezone().trim()); }
        catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid clinic timezone"); }
        jdbc.update("""
                UPDATE clinics SET name = ?, address_line = ?, city = ?, region = ?, postal_code = ?, phone = ?, timezone = ?, updated_at = now()
                WHERE id = ? AND active = true
                """, request.clinicName().trim(), clean(request.addressLine()), clean(request.city()), clean(request.region()),
                clean(request.postalCode()), clean(request.phone()), request.timezone().trim(), clinicId);
        jdbc.update("""
                UPDATE doctor_profiles SET specialization = ?, qualification = ?, registration_number = ?,
                    consultation_fee = ?, consultation_duration_minutes = ?, updated_at = now()
                WHERE id = ? AND clinic_id = ? AND active = true
                """, clean(request.specialization()), clean(request.qualification()), clean(request.registrationNumber()),
                request.consultationFee(), request.consultationDurationMinutes(), doctorId, clinicId);
        jdbc.update("""
                INSERT INTO clinic_settings (clinic_id, setting_key, setting_value, updated_by_user_id, updated_at)
                VALUES (?, 'appointment_policy', jsonb_build_object('cancellationHours', ?, 'bookingWindowDays', ?), ?, now())
                ON CONFLICT (clinic_id, setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value,
                    updated_by_user_id = EXCLUDED.updated_by_user_id, updated_at = now()
                """, clinicId, request.cancellationHours(), request.bookingWindowDays(), userId);
        jdbc.update("""
                INSERT INTO audit_logs (clinic_id, actor_user_id, action, entity_type, entity_id, after_data)
                VALUES (?, ?, 'CLINIC_SETTINGS_UPDATED', 'clinic', ?, jsonb_build_object('clinicName', ?))
                """, clinicId, userId, clinicId, request.clinicName().trim());
        return row(doctorId, clinicId);
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> services(FirebasePrincipal principal, UUID clinicId) {
        doctorProfileId(userId(principal), clinicId);
        return jdbc.query("SELECT id, name, description, fee, active FROM services WHERE clinic_id = ? ORDER BY active DESC, name",
                (rs, index) -> new ServiceDto(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("description"), rs.getBigDecimal("fee"), rs.getBoolean("active")), clinicId);
    }

    @Transactional
    public ServiceDto saveService(FirebasePrincipal principal, UUID clinicId, UUID serviceId, ServiceRequest request) {
        UUID actorId = userId(principal);
        doctorProfileId(actorId, clinicId);
        String name = request.name().trim();
        if (serviceId == null) {
            serviceId = jdbc.queryForObject("""
                    INSERT INTO services (clinic_id, name, description, fee)
                    VALUES (?, ?, ?, ?) RETURNING id
                    """, UUID.class, clinicId, name, clean(request.description()), request.fee());
        } else if (jdbc.update("""
                UPDATE services SET name = ?, description = ?, fee = ?
                WHERE id = ? AND clinic_id = ? AND active = true
                """, name, clean(request.description()), request.fee(), serviceId, clinicId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active clinic service not found");
        }
        jdbc.update("""
                INSERT INTO audit_logs (clinic_id, actor_user_id, action, entity_type, entity_id, after_data)
                VALUES (?, ?, 'CLINIC_SERVICE_SAVED', 'service', ?, jsonb_build_object('name', ?, 'fee', ?))
                """, clinicId, actorId, serviceId, name, request.fee());
        return service(clinicId, serviceId);
    }

    @Transactional
    public void deactivateService(FirebasePrincipal principal, UUID clinicId, UUID serviceId) {
        UUID actorId = userId(principal);
        doctorProfileId(actorId, clinicId);
        if (jdbc.update("UPDATE services SET active = false WHERE id = ? AND clinic_id = ? AND active = true", serviceId, clinicId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active clinic service not found");
        }
        jdbc.update("""
                INSERT INTO audit_logs (clinic_id, actor_user_id, action, entity_type, entity_id)
                VALUES (?, ?, 'CLINIC_SERVICE_DEACTIVATED', 'service', ?)
                """, clinicId, actorId, serviceId);
    }

    private ServiceDto service(UUID clinicId, UUID serviceId) {
        return jdbc.query("SELECT id, name, description, fee, active FROM services WHERE id = ? AND clinic_id = ?",
                (rs, index) -> new ServiceDto(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("description"), rs.getBigDecimal("fee"), rs.getBoolean("active")),
                serviceId, clinicId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinic service not found"));
    }

    private SettingsDto row(UUID doctorId, UUID clinicId) {
        return jdbc.query("""
                SELECT c.id, c.name, c.address_line, c.city, c.region, c.postal_code, c.phone, c.timezone,
                       dp.specialization, dp.qualification, dp.registration_number, dp.consultation_fee,
                       dp.consultation_duration_minutes,
                       COALESCE((SELECT (setting_value->>'cancellationHours')::integer FROM clinic_settings
                                 WHERE clinic_id = c.id AND setting_key = 'appointment_policy'), 24) AS cancellation_hours,
                       COALESCE((SELECT (setting_value->>'bookingWindowDays')::integer FROM clinic_settings
                                 WHERE clinic_id = c.id AND setting_key = 'appointment_policy'), 90) AS booking_window_days
                FROM clinics c JOIN doctor_profiles dp ON dp.clinic_id = c.id
                WHERE c.id = ? AND dp.id = ? AND c.active = true AND dp.active = true
                """, (rs, index) -> new SettingsDto(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("address_line"), rs.getString("city"), rs.getString("region"), rs.getString("postal_code"),
                rs.getString("phone"), rs.getString("timezone"), rs.getString("specialization"), rs.getString("qualification"),
                rs.getString("registration_number"), rs.getBigDecimal("consultation_fee"),
                rs.getInt("consultation_duration_minutes"), rs.getInt("cancellation_hours"),
                rs.getInt("booking_window_days")), clinicId, doctorId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinic settings not found"));
    }

    private UUID doctorProfileId(UUID userId, UUID clinicId) {
        return jdbc.query("""
                SELECT dp.id FROM doctor_profiles dp JOIN clinic_members cm ON cm.id = dp.clinic_member_id
                JOIN clinics c ON c.id = dp.clinic_id
                WHERE cm.user_id = ? AND dp.clinic_id = ? AND cm.role = 'DOCTOR'
                  AND cm.active = true AND dp.active = true AND c.active = true
                """, (rs, row) -> rs.getObject("id", UUID.class), userId, clinicId).stream().findFirst()
                .orElseThrow(() -> new AccessDeniedException("Doctor access is not enabled for this clinic"));
    }

    private UUID userId(FirebasePrincipal principal) {
        if (principal == null || principal.uid() == null || principal.uid().isBlank()) throw new AccessDeniedException("Sign in is required");
        try { return jdbc.queryForObject("SELECT id FROM users WHERE firebase_uid = ? AND status = 'ACTIVE'", UUID.class, principal.uid()); }
        catch (EmptyResultDataAccessException exception) { throw new AccessDeniedException("Active account not found"); }
    }

    private String clean(String value) { if (value == null) return null; String text = value.trim(); return text.isEmpty() ? null : text; }
}
