package com.clinic.staff;

import static com.clinic.staff.StaffDtos.*;

import com.clinic.appointment.AppointmentDtos.AppointmentDto;
import com.clinic.patient.PatientDtos.DoctorPatientDto;
import com.clinic.security.FirebasePrincipal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StaffService {
    private static final Set<String> ALLOWED_PERMISSIONS = Set.of(
            "PATIENT_READ", "PATIENT_REGISTER", "APPOINTMENT_READ", "APPOINTMENT_MANAGE",
            "QUEUE_MANAGE", "VITALS_WRITE", "BILLING_READ", "BILLING_WRITE", "REPORT_UPLOAD");
    private static final SecureRandom RANDOM = new SecureRandom();
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

    public StaffService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public InvitationDto invite(FirebasePrincipal principal, UUID clinicId, InvitationRequest request) {
        UUID doctorId = userId(principal);
        requireDoctor(doctorId, clinicId);
        validatePermissions(request.permissions());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        jdbc.update("""
                UPDATE staff_invitations SET revoked_at = now()
                WHERE clinic_id = ? AND lower(invited_email) = ? AND accepted_at IS NULL AND revoked_at IS NULL
                """, clinicId, email);
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        String tokenHash = sha256(token);
        UUID invitationId = jdbc.queryForObject("""
                INSERT INTO staff_invitations (clinic_id, invited_email, token_hash, designation, expires_at, invited_by_user_id)
                VALUES (?, ?, ?, ?, now() + interval '7 days', ?)
                RETURNING id
                """, UUID.class, clinicId, email, tokenHash, clean(request.designation()), doctorId);
        for (String permission : request.permissions()) {
            jdbc.update("INSERT INTO staff_invitation_permissions (invitation_id, permission) VALUES (?, ?)", invitationId, permission);
        }
        audit(clinicId, doctorId, "STAFF_INVITED", invitationId, "email", email);
        return new InvitationDto(invitationId, email, clean(request.designation()), request.permissions().stream().sorted().toList(),
                jdbc.queryForObject("SELECT expires_at FROM staff_invitations WHERE id = ?", OffsetDateTime.class, invitationId),
                null, null, token);
    }

    @Transactional(readOnly = true)
    public List<InvitationDto> invitations(FirebasePrincipal principal, UUID clinicId) {
        requireDoctor(userId(principal), clinicId);
        List<UUID> ids = jdbc.query("""
                SELECT id FROM staff_invitations WHERE clinic_id = ?
                ORDER BY created_at DESC LIMIT 100
                """, (rs, row) -> rs.getObject("id", UUID.class), clinicId);
        return ids.stream().map(id -> {
            InvitationRow row = jdbc.queryForObject("""
                    SELECT id, invited_email, designation, expires_at, accepted_at, revoked_at
                    FROM staff_invitations WHERE id = ?
                    """, (rs, index) -> new InvitationRow(rs.getObject("id", UUID.class), rs.getString("invited_email"),
                    rs.getString("designation"), rs.getObject("expires_at", OffsetDateTime.class),
                    rs.getObject("accepted_at", OffsetDateTime.class), rs.getObject("revoked_at", OffsetDateTime.class)), id);
            return new InvitationDto(row.id(), row.email(), row.designation(), invitationPermissions(id),
                    row.expiresAt(), row.acceptedAt(), row.revokedAt(), null);
        }).toList();
    }

    @Transactional
    public void revokeInvitation(FirebasePrincipal principal, UUID clinicId, UUID invitationId) {
        UUID actorId = userId(principal);
        requireDoctor(actorId, clinicId);
        if (jdbc.update("""
                UPDATE staff_invitations SET revoked_at = now()
                WHERE id = ? AND clinic_id = ? AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at > now()
                """, invitationId, clinicId) != 1) {
            throw notFound("Pending staff invitation not found");
        }
        audit(clinicId, actorId, "STAFF_INVITATION_REVOKED", invitationId, "status", "revoked");
    }

    @Transactional
    public StaffMemberDto acceptInvitation(FirebasePrincipal principal, String rawToken) {
        if (principal == null || principal.email() == null || !principal.emailVerified()) {
            throw new AccessDeniedException("Sign in with a verified Firebase email before accepting a staff invitation");
        }
        String tokenHash = sha256(rawToken.trim());
        InvitationAccept invitation = jdbc.query("""
                SELECT id, clinic_id, invited_email, designation
                FROM staff_invitations
                WHERE token_hash = ? AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at > now()
                FOR UPDATE
                """, (rs, row) -> new InvitationAccept(rs.getObject("id", UUID.class),
                rs.getObject("clinic_id", UUID.class), rs.getString("invited_email"), rs.getString("designation")), tokenHash)
                .stream().findFirst().orElseThrow(() -> notFound("Invitation is invalid, expired, or already used"));
        if (!invitation.email().equalsIgnoreCase(principal.email())) {
            throw new AccessDeniedException("This invitation was issued to a different email address");
        }
        UUID userId = userId(principal);
        UUID memberId = jdbc.query("SELECT id FROM clinic_members WHERE clinic_id = ? AND user_id = ?",
                (rs, row) -> rs.getObject("id", UUID.class), invitation.clinicId(), userId).stream().findFirst().orElse(null);
        if (memberId == null) {
            memberId = jdbc.queryForObject("""
                    INSERT INTO clinic_members (clinic_id, user_id, role, active)
                    VALUES (?, ?, 'STAFF', true) RETURNING id
                    """, UUID.class, invitation.clinicId(), userId);
        } else {
            String existingRole = jdbc.queryForObject("SELECT role FROM clinic_members WHERE id = ? FOR UPDATE", String.class, memberId);
            if (!"STAFF".equals(existingRole)) throw conflict("This account already has another role in this clinic");
            jdbc.update("UPDATE clinic_members SET active = true WHERE id = ?", memberId);
        }
        jdbc.update("DELETE FROM staff_permissions WHERE clinic_id = ? AND clinic_member_id = ?", invitation.clinicId(), memberId);
        jdbc.update("""
                INSERT INTO staff_profiles (clinic_id, clinic_member_id, designation, active)
                VALUES (?, ?, ?, true)
                ON CONFLICT (clinic_member_id) DO UPDATE SET designation = EXCLUDED.designation, active = true
                """, invitation.clinicId(), memberId, invitation.designation());
        jdbc.update("""
                INSERT INTO staff_permissions (clinic_id, clinic_member_id, permission, granted_by_user_id)
                SELECT ?, ?, sip.permission, si.invited_by_user_id
                FROM staff_invitation_permissions sip
                JOIN staff_invitations si ON si.id = sip.invitation_id
                WHERE sip.invitation_id = ?
                ON CONFLICT (clinic_member_id, permission) DO NOTHING
                """, invitation.clinicId(), memberId, invitation.id());
        jdbc.update("UPDATE staff_invitations SET accepted_at = now() WHERE id = ?", invitation.id());
        audit(invitation.clinicId(), userId, "STAFF_INVITATION_ACCEPTED", memberId, "invitationId", invitation.id().toString());
        return staffMember(invitation.clinicId(), memberId);
    }

    @Transactional(readOnly = true)
    public List<StaffMemberDto> members(FirebasePrincipal principal, UUID clinicId) {
        requireDoctor(userId(principal), clinicId);
        List<UUID> ids = jdbc.query("""
                SELECT cm.id FROM clinic_members cm
                LEFT JOIN staff_profiles sp ON sp.clinic_member_id = cm.id
                WHERE cm.clinic_id = ? AND cm.role = 'STAFF'
                ORDER BY cm.active DESC, sp.designation, cm.created_at DESC
                """, (rs, row) -> rs.getObject("id", UUID.class), clinicId);
        return ids.stream().map(id -> staffMember(clinicId, id)).toList();
    }

    @Transactional
    public StaffMemberDto updatePermissions(FirebasePrincipal principal, UUID clinicId, UUID memberId, PermissionsRequest request) {
        UUID actorId = userId(principal);
        requireDoctor(actorId, clinicId);
        validatePermissions(request.permissions());
        requireStaffMember(clinicId, memberId);
        jdbc.update("DELETE FROM staff_permissions WHERE clinic_id = ? AND clinic_member_id = ?", clinicId, memberId);
        for (String permission : request.permissions()) {
            jdbc.update("""
                    INSERT INTO staff_permissions (clinic_id, clinic_member_id, permission, granted_by_user_id)
                    VALUES (?, ?, ?, ?)
                    """, clinicId, memberId, permission, actorId);
        }
        audit(clinicId, actorId, "STAFF_PERMISSIONS_CHANGED", memberId, "permissions", String.join(",", request.permissions()));
        return staffMember(clinicId, memberId);
    }

    @Transactional
    public void deactivate(FirebasePrincipal principal, UUID clinicId, UUID memberId) {
        UUID actorId = userId(principal);
        requireDoctor(actorId, clinicId);
        requireStaffMember(clinicId, memberId);
        jdbc.update("UPDATE clinic_members SET active = false WHERE id = ? AND clinic_id = ?", memberId, clinicId);
        jdbc.update("UPDATE staff_profiles SET active = false WHERE clinic_id = ? AND clinic_member_id = ?", clinicId, memberId);
        jdbc.update("DELETE FROM staff_permissions WHERE clinic_id = ? AND clinic_member_id = ?", clinicId, memberId);
        audit(clinicId, actorId, "STAFF_DEACTIVATED", memberId, "active", "false");
    }

    @Transactional(readOnly = true)
    public StaffDashboardDto dashboard(FirebasePrincipal principal, UUID clinicId) {
        UUID memberId = staffMemberId(userId(principal), clinicId);
        ZoneId zone = clinicZone(clinicId);
        OffsetDateTime start = LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        return jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE a.starts_at >= ? AND a.starts_at < ?) AS today_appointments,
                       COUNT(*) FILTER (WHERE a.status = 'CHECKED_IN' AND a.starts_at >= ? AND a.starts_at < ?) AS waiting_patients,
                       COUNT(*) FILTER (WHERE a.status = 'COMPLETED' AND a.starts_at >= ? AND a.starts_at < ?) AS completed_today,
                       (SELECT COUNT(*) FROM clinic_patients WHERE clinic_id = ? AND active = true) AS total_patients
                FROM appointments a WHERE a.clinic_id = ?
                """, (rs, row) -> new StaffDashboardDto(rs.getLong("today_appointments"),
                rs.getLong("waiting_patients"), rs.getLong("completed_today"), rs.getLong("total_patients")),
                start, end, start, end, start, end, clinicId, clinicId);
    }

    @Transactional(readOnly = true)
    public StaffMemberDto currentStaff(FirebasePrincipal principal, UUID clinicId) {
        UUID memberId = staffMemberId(userId(principal), clinicId);
        return staffMember(clinicId, memberId);
    }

    @Transactional(readOnly = true)
    public List<DoctorPatientDto> patients(FirebasePrincipal principal, UUID clinicId, String search) {
        UUID memberId = staffMemberId(userId(principal), clinicId);
        requirePermission(clinicId, memberId, "PATIENT_READ", "BILLING_READ", "BILLING_WRITE");
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return jdbc.query("""
                SELECT cp.id AS clinic_patient_id, cp.patient_code,
                       COALESCE(pp.preferred_name, u.full_name) AS full_name, u.email, u.phone,
                       COUNT(a.id) AS appointment_count, MAX(a.starts_at) AS last_appointment_at
                FROM clinic_patients cp JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                JOIN users u ON u.id = pp.user_id AND u.status = 'ACTIVE'
                LEFT JOIN appointments a ON a.clinic_patient_id = cp.id AND a.clinic_id = cp.clinic_id
                WHERE cp.clinic_id = ? AND cp.active = true
                  AND (? = '' OR lower(u.full_name) LIKE '%' || ? || '%' OR lower(COALESCE(pp.preferred_name, '')) LIKE '%' || ? || '%'
                       OR lower(u.email) LIKE '%' || ? || '%' OR lower(COALESCE(u.phone, '')) LIKE '%' || ? || '%'
                       OR lower(cp.patient_code) LIKE '%' || ? || '%')
                GROUP BY cp.id, cp.patient_code, pp.preferred_name, u.full_name, u.email, u.phone
                ORDER BY MAX(a.starts_at) DESC NULLS LAST, full_name LIMIT 200
                """, (rs, row) -> new DoctorPatientDto(rs.getObject("clinic_patient_id", UUID.class),
                rs.getString("patient_code"), rs.getString("full_name"), rs.getString("email"), rs.getString("phone"),
                rs.getLong("appointment_count"), rs.getObject("last_appointment_at", OffsetDateTime.class)),
                clinicId, term, term, term, term, term, term);
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> appointments(FirebasePrincipal principal, UUID clinicId, LocalDate date) {
        UUID userId = userId(principal);
        UUID memberId = staffMemberId(userId, clinicId);
        requirePermission(clinicId, memberId, "APPOINTMENT_READ", "QUEUE_MANAGE");
        ZoneId zone = clinicZone(clinicId);
        OffsetDateTime start = date.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        return jdbc.query(APPOINTMENT_SELECT + " WHERE a.clinic_id = ? AND a.starts_at >= ? AND a.starts_at < ? ORDER BY a.starts_at LIMIT 500",
                (rs, row) -> new AppointmentDto(rs.getObject("id", UUID.class), rs.getObject("clinic_id", UUID.class),
                        rs.getObject("clinic_patient_id", UUID.class), rs.getString("clinic_name"),
                        rs.getObject("doctor_profile_id", UUID.class), rs.getString("doctor_name"), rs.getString("specialization"),
                        rs.getString("patient_code"), rs.getString("patient_name"), rs.getString("patient_email"),
                        rs.getObject("starts_at", OffsetDateTime.class), rs.getObject("ends_at", OffsetDateTime.class),
                        rs.getString("status"), rs.getString("reason"), rs.getString("cancellation_reason"),
                        rs.getObject("created_at", OffsetDateTime.class)), clinicId, start, end);
    }

    @Transactional
    public AppointmentDto checkIn(FirebasePrincipal principal, UUID clinicId, UUID appointmentId) {
        UUID actorId = userId(principal);
        UUID memberId = staffMemberId(actorId, clinicId);
        requirePermission(clinicId, memberId, "QUEUE_MANAGE");
        AppointmentStatus status = jdbc.query("SELECT status, starts_at FROM appointments WHERE id = ? AND clinic_id = ? FOR UPDATE",
                (rs, row) -> new AppointmentStatus(rs.getString("status"), rs.getObject("starts_at", OffsetDateTime.class)),
                appointmentId, clinicId).stream().findFirst().orElseThrow(() -> notFound("Appointment not found"));
        if (!status.startsAt().atZoneSameInstant(clinicZone(clinicId)).toLocalDate().equals(LocalDate.now(clinicZone(clinicId)))) {
            throw conflict("Only appointments scheduled for today can be checked in");
        }
        if (!"CONFIRMED".equals(status.status())) throw conflict("Only confirmed appointments can be checked in");
        jdbc.update("UPDATE appointments SET status = 'CHECKED_IN', updated_at = now() WHERE id = ? AND clinic_id = ?",
                appointmentId, clinicId);
        jdbc.update("""
                INSERT INTO appointment_status_history (clinic_id, appointment_id, previous_status, new_status, changed_by_user_id, reason)
                VALUES (?, ?, 'CONFIRMED', 'CHECKED_IN', ?, 'Checked in by staff')
                """, clinicId, appointmentId, actorId);
        audit(clinicId, actorId, "PATIENT_CHECKED_IN", appointmentId, "status", "CHECKED_IN");
        return jdbc.query(APPOINTMENT_SELECT + " WHERE a.id = ? AND a.clinic_id = ?", (rs, row) ->
                new AppointmentDto(rs.getObject("id", UUID.class), rs.getObject("clinic_id", UUID.class),
                        rs.getObject("clinic_patient_id", UUID.class), rs.getString("clinic_name"),
                        rs.getObject("doctor_profile_id", UUID.class), rs.getString("doctor_name"), rs.getString("specialization"),
                        rs.getString("patient_code"), rs.getString("patient_name"), rs.getString("patient_email"),
                        rs.getObject("starts_at", OffsetDateTime.class), rs.getObject("ends_at", OffsetDateTime.class),
                        rs.getString("status"), rs.getString("reason"), rs.getString("cancellation_reason"),
                        rs.getObject("created_at", OffsetDateTime.class)), appointmentId, clinicId).stream().findFirst()
                .orElseThrow(() -> notFound("Appointment not found"));
    }

    private StaffMemberDto staffMember(UUID clinicId, UUID memberId) {
        StaffMemberRow row = jdbc.query("""
                SELECT cm.id, cm.clinic_id, u.full_name, u.email, sp.designation, cm.active
                FROM clinic_members cm JOIN users u ON u.id = cm.user_id
                LEFT JOIN staff_profiles sp ON sp.clinic_member_id = cm.id AND sp.clinic_id = cm.clinic_id
                WHERE cm.id = ? AND cm.clinic_id = ? AND cm.role = 'STAFF'
                """, (rs, index) -> new StaffMemberRow(rs.getObject("id", UUID.class),
                rs.getObject("clinic_id", UUID.class), rs.getString("full_name"), rs.getString("email"),
                rs.getString("designation"), rs.getBoolean("active")), memberId, clinicId).stream().findFirst()
                .orElseThrow(() -> notFound("Staff member not found"));
        return new StaffMemberDto(row.id(), row.clinicId(), row.fullName(), row.email(), row.designation(),
                row.active(), permissions(memberId));
    }

    private UUID staffMemberId(UUID userId, UUID clinicId) {
        return jdbc.query("""
                SELECT cm.id FROM clinic_members cm JOIN clinics c ON c.id = cm.clinic_id
                WHERE cm.user_id = ? AND cm.clinic_id = ? AND cm.role = 'STAFF' AND cm.active = true AND c.active = true
                """, (rs, row) -> rs.getObject("id", UUID.class), userId, clinicId).stream().findFirst()
                .orElseThrow(() -> new AccessDeniedException("Staff access is not enabled for this clinic"));
    }

    private void requireDoctor(UUID userId, UUID clinicId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM clinic_members cm JOIN clinics c ON c.id = cm.clinic_id
                WHERE cm.user_id = ? AND cm.clinic_id = ? AND cm.role = 'DOCTOR' AND cm.active = true AND c.active = true
                """, Integer.class, userId, clinicId);
        if (count == null || count == 0) throw new AccessDeniedException("Doctor access is not enabled for this clinic");
    }

    private void requireStaffMember(UUID clinicId, UUID memberId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM clinic_members WHERE id = ? AND clinic_id = ? AND role = 'STAFF'",
                Integer.class, memberId, clinicId);
        if (count == null || count == 0) throw notFound("Staff member not found");
    }

    private void requirePermission(UUID clinicId, UUID memberId, String... permissions) {
        List<String> granted = permissions(memberId);
        if (java.util.Arrays.stream(permissions).noneMatch(granted::contains)) {
            throw new AccessDeniedException("Your staff account does not have permission for this action");
        }
    }

    private void validatePermissions(Set<String> requested) {
        if (requested == null || requested.isEmpty() || !ALLOWED_PERMISSIONS.containsAll(requested)) {
            throw badRequest("Select one or more supported staff permissions");
        }
    }

    private List<String> permissions(UUID memberId) {
        return jdbc.query("SELECT permission FROM staff_permissions WHERE clinic_member_id = ? ORDER BY permission",
                (rs, row) -> rs.getString("permission"), memberId);
    }

    private List<String> invitationPermissions(UUID invitationId) {
        return jdbc.query("SELECT permission FROM staff_invitation_permissions WHERE invitation_id = ? ORDER BY permission",
                (rs, row) -> rs.getString("permission"), invitationId);
    }

    private UUID userId(FirebasePrincipal principal) {
        if (principal == null || principal.uid() == null || principal.uid().isBlank()) throw new AccessDeniedException("Sign in is required");
        try { return jdbc.queryForObject("SELECT id FROM users WHERE firebase_uid = ? AND status = 'ACTIVE'", UUID.class, principal.uid()); }
        catch (EmptyResultDataAccessException exception) { throw new AccessDeniedException("Active account not found"); }
    }

    private ZoneId clinicZone(UUID clinicId) {
        String timezone = jdbc.query("SELECT timezone FROM clinics WHERE id = ? AND active = true",
                (rs, row) -> rs.getString("timezone"), clinicId).stream().findFirst()
                .orElseThrow(() -> notFound("Clinic not found"));
        return ZoneId.of(timezone);
    }

    private String sha256(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private void audit(UUID clinicId, UUID actorId, String action, UUID entityId, String key, String value) {
        jdbc.update("""
                INSERT INTO audit_logs (clinic_id, actor_user_id, action, entity_type, entity_id, after_data)
                VALUES (?, ?, ?, 'staff', ?, jsonb_build_object(?, ?))
                """, clinicId, actorId, action, entityId, key, value);
    }

    private String clean(String value) { if (value == null) return null; String text = value.trim(); return text.isEmpty() ? null : text; }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    private record InvitationRow(UUID id, String email, String designation, OffsetDateTime expiresAt, OffsetDateTime acceptedAt, OffsetDateTime revokedAt) {}
    private record InvitationAccept(UUID id, UUID clinicId, String email, String designation) {}
    private record StaffMemberRow(UUID id, UUID clinicId, String fullName, String email, String designation, boolean active) {}
    private record AppointmentStatus(String status, OffsetDateTime startsAt) {}
}
