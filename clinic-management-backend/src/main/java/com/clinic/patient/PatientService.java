package com.clinic.patient;

import static com.clinic.patient.PatientDtos.*;

import com.clinic.security.FirebasePrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PatientService {
    private final JdbcTemplate jdbc;

    public PatientService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public ProfileDto profile(FirebasePrincipal principal) {
        UUID userId = userId(principal);
        return jdbc.query("""
                SELECT u.full_name, u.email, u.phone, pp.date_of_birth, pp.sex_at_birth, pp.preferred_name,
                       pp.emergency_contact_name, pp.emergency_contact_phone
                FROM users u JOIN patient_profiles pp ON pp.user_id = u.id
                WHERE u.id = ? AND u.status = 'ACTIVE'
                """, (rs, row) -> new ProfileDto(rs.getString("full_name"), rs.getString("email"), rs.getString("phone"),
                rs.getObject("date_of_birth", LocalDate.class), rs.getString("sex_at_birth"), rs.getString("preferred_name"),
                rs.getString("emergency_contact_name"), rs.getString("emergency_contact_phone")), userId).stream().findFirst()
                .orElseThrow(() -> new AccessDeniedException("A patient profile is required"));
    }

    @Transactional
    public ProfileDto updateProfile(FirebasePrincipal principal, ProfileRequest request) {
        UUID userId = userId(principal);
        if (request.dateOfBirth() != null && (request.dateOfBirth().isAfter(LocalDate.now()) || request.dateOfBirth().isBefore(LocalDate.of(1900, 1, 1)))) {
            throw badRequest("Enter a valid date of birth");
        }
        String name = request.fullName().trim();
        jdbc.update("UPDATE users SET full_name = ?, phone = ?, updated_at = now() WHERE id = ? AND status = 'ACTIVE'",
                name, clean(request.phone()), userId);
        int changed = jdbc.update("""
                UPDATE patient_profiles
                SET date_of_birth = ?, sex_at_birth = ?, preferred_name = ?, emergency_contact_name = ?,
                    emergency_contact_phone = ?, updated_at = now()
                WHERE user_id = ?
                """, request.dateOfBirth(), clean(request.sexAtBirth()), clean(request.preferredName()),
                clean(request.emergencyContactName()), clean(request.emergencyContactPhone()), userId);
        if (changed != 1) throw new AccessDeniedException("A patient profile is required");
        return profile(principal);
    }

    @Transactional(readOnly = true)
    public List<FamilyMemberDto> family(FirebasePrincipal principal, UUID clinicId) {
        UUID clinicPatientId = clinicPatientFor(userId(principal), clinicId, false);
        if (clinicPatientId == null) return List.of();
        return jdbc.query("""
                SELECT id, full_name, relationship, date_of_birth, phone, authorized_by_patient
                FROM family_members WHERE clinic_id = ? AND clinic_patient_id = ?
                ORDER BY authorized_by_patient DESC, full_name
                """, (rs, row) -> new FamilyMemberDto(rs.getObject("id", UUID.class), rs.getString("full_name"),
                rs.getString("relationship"), rs.getObject("date_of_birth", LocalDate.class), rs.getString("phone"),
                rs.getBoolean("authorized_by_patient")), clinicId, clinicPatientId);
    }

    @Transactional
    public FamilyMemberDto addFamily(FirebasePrincipal principal, UUID clinicId, FamilyRequest request) {
        UUID userId = userId(principal);
        validateDate(request.dateOfBirth());
        UUID clinicPatientId = clinicPatientFor(userId, clinicId, true);
        UUID id = jdbc.queryForObject("""
                INSERT INTO family_members (clinic_id, clinic_patient_id, full_name, relationship, date_of_birth, phone, authorized_by_patient)
                VALUES (?, ?, ?, ?, ?, ?, true) RETURNING id
                """, UUID.class, clinicId, clinicPatientId, request.fullName().trim(), request.relationship().trim(),
                request.dateOfBirth(), clean(request.phone()));
        audit(clinicId, userId, "FAMILY_MEMBER_ADDED", id, "authorized", "true");
        return familyMember(clinicId, clinicPatientId, id);
    }

    @Transactional
    public FamilyMemberDto updateFamily(FirebasePrincipal principal, UUID clinicId, UUID familyMemberId, FamilyRequest request) {
        UUID userId = userId(principal);
        validateDate(request.dateOfBirth());
        UUID clinicPatientId = clinicPatientFor(userId, clinicId, false);
        if (clinicPatientId == null) throw notFound("Family member not found");
        int changed = jdbc.update("""
                UPDATE family_members SET full_name = ?, relationship = ?, date_of_birth = ?, phone = ?
                WHERE id = ? AND clinic_id = ? AND clinic_patient_id = ? AND authorized_by_patient = true
                """, request.fullName().trim(), request.relationship().trim(), request.dateOfBirth(), clean(request.phone()),
                familyMemberId, clinicId, clinicPatientId);
        if (changed != 1) throw notFound("Family member not found or access was revoked");
        audit(clinicId, userId, "FAMILY_MEMBER_UPDATED", familyMemberId, "updated", "true");
        return familyMember(clinicId, clinicPatientId, familyMemberId);
    }

    @Transactional
    public FamilyMemberDto revokeFamily(FirebasePrincipal principal, UUID clinicId, UUID familyMemberId) {
        UUID userId = userId(principal);
        UUID clinicPatientId = clinicPatientFor(userId, clinicId, false);
        if (clinicPatientId == null) throw notFound("Family member not found");
        int changed = jdbc.update("""
                UPDATE family_members SET authorized_by_patient = false
                WHERE id = ? AND clinic_id = ? AND clinic_patient_id = ? AND authorized_by_patient = true
                """, familyMemberId, clinicId, clinicPatientId);
        if (changed != 1) throw notFound("Family member not found");
        audit(clinicId, userId, "FAMILY_MEMBER_AUTHORIZATION_REVOKED", familyMemberId, "authorized", "false");
        return familyMember(clinicId, clinicPatientId, familyMemberId);
    }

    @Transactional(readOnly = true)
    public List<DoctorPatientDto> doctorPatients(FirebasePrincipal principal, UUID clinicId, String search) {
        UUID doctorUserId = userId(principal);
        requireDoctor(doctorUserId, clinicId);
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return jdbc.query("""
                SELECT cp.id AS clinic_patient_id, cp.patient_code,
                       COALESCE(pp.preferred_name, u.full_name) AS full_name, u.email, u.phone,
                       COUNT(a.id) AS appointment_count, MAX(a.starts_at) AS last_appointment_at
                FROM clinic_patients cp
                JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                JOIN users u ON u.id = pp.user_id AND u.status = 'ACTIVE'
                LEFT JOIN appointments a ON a.clinic_patient_id = cp.id AND a.clinic_id = cp.clinic_id
                WHERE cp.clinic_id = ? AND cp.active = true
                  AND (? = '' OR lower(u.full_name) LIKE '%' || ? || '%' OR lower(COALESCE(pp.preferred_name, '')) LIKE '%' || ? || '%'
                       OR lower(u.email) LIKE '%' || ? || '%' OR lower(COALESCE(u.phone, '')) LIKE '%' || ? || '%' OR lower(cp.patient_code) LIKE '%' || ? || '%')
                GROUP BY cp.id, cp.patient_code, pp.preferred_name, u.full_name, u.email, u.phone
                ORDER BY MAX(a.starts_at) DESC NULLS LAST, full_name LIMIT 200
                """, (rs, row) -> new DoctorPatientDto(rs.getObject("clinic_patient_id", UUID.class),
                rs.getString("patient_code"), rs.getString("full_name"), rs.getString("email"), rs.getString("phone"),
                rs.getLong("appointment_count"), rs.getObject("last_appointment_at", OffsetDateTime.class)),
                clinicId, term, term, term, term, term, term);
    }

    @Transactional
    public DoctorPatientDetailDto doctorPatient(FirebasePrincipal principal, UUID clinicId, UUID clinicPatientId) {
        UUID doctorUserId = userId(principal);
        requireDoctor(doctorUserId, clinicId);
        DoctorPatientDto patient = doctorPatientRow(clinicId, clinicPatientId);
        ProfileDto profile = jdbc.query("""
                SELECT u.full_name, u.email, u.phone, pp.date_of_birth, pp.sex_at_birth, pp.preferred_name,
                       pp.emergency_contact_name, pp.emergency_contact_phone
                FROM clinic_patients cp JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                JOIN users u ON u.id = pp.user_id
                WHERE cp.id = ? AND cp.clinic_id = ?
                """, (rs, row) -> new ProfileDto(rs.getString("full_name"), rs.getString("email"), rs.getString("phone"),
                rs.getObject("date_of_birth", LocalDate.class), rs.getString("sex_at_birth"), rs.getString("preferred_name"),
                rs.getString("emergency_contact_name"), rs.getString("emergency_contact_phone")), clinicPatientId, clinicId)
                .stream().findFirst().orElseThrow(() -> notFound("Patient not found"));
        List<MedicalHistoryDto> history = jdbc.query("""
                SELECT mh.id, mh.category, mh.description, mh.recorded_at, u.full_name AS recorded_by
                FROM medical_history mh JOIN users u ON u.id = mh.recorded_by_user_id
                WHERE mh.clinic_id = ? AND mh.clinic_patient_id = ? ORDER BY mh.recorded_at DESC LIMIT 200
                """, (rs, row) -> new MedicalHistoryDto(rs.getObject("id", UUID.class), rs.getString("category"),
                rs.getString("description"), rs.getObject("recorded_at", OffsetDateTime.class), rs.getString("recorded_by")), clinicId, clinicPatientId);
        List<AllergyDto> allergies = jdbc.query("""
                SELECT id, allergen, reaction, severity, notes, created_at
                FROM patient_allergies WHERE clinic_id = ? AND clinic_patient_id = ? ORDER BY allergen
                """, (rs, row) -> new AllergyDto(rs.getObject("id", UUID.class), rs.getString("allergen"),
                rs.getString("reaction"), rs.getString("severity"), rs.getString("notes"),
                rs.getObject("created_at", OffsetDateTime.class)), clinicId, clinicPatientId);
        List<VisitSummaryDto> visits = jdbc.query("""
                SELECT c.id, c.started_at, c.finalized_at, c.symptoms, c.diagnosis, c.treatment_plan, c.doctor_notes
                FROM consultations c WHERE c.clinic_id = ? AND c.clinic_patient_id = ? AND c.finalized_at IS NOT NULL
                ORDER BY c.finalized_at DESC LIMIT 50
                """, (rs, row) -> new VisitSummaryDto(rs.getObject("id", UUID.class),
                rs.getObject("started_at", OffsetDateTime.class), rs.getObject("finalized_at", OffsetDateTime.class),
                rs.getString("symptoms"), rs.getString("diagnosis"), rs.getString("treatment_plan"), rs.getString("doctor_notes")),
                clinicId, clinicPatientId);
        audit(clinicId, doctorUserId, "PATIENT_RECORD_VIEWED", clinicPatientId, "patientCode", patient.patientCode());
        return new DoctorPatientDetailDto(patient, profile, history, allergies, visits);
    }

    @Transactional
    public MedicalHistoryDto addMedicalHistory(FirebasePrincipal principal, UUID clinicId, UUID clinicPatientId,
            MedicalHistoryRequest request) {
        UUID doctorUserId = userId(principal);
        requireDoctor(doctorUserId, clinicId);
        requireClinicPatient(clinicId, clinicPatientId);
        UUID id = jdbc.queryForObject("""
                INSERT INTO medical_history (clinic_id, clinic_patient_id, category, description, recorded_by_user_id)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, UUID.class, clinicId, clinicPatientId, request.category().trim(), request.description().trim(), doctorUserId);
        audit(clinicId, doctorUserId, "MEDICAL_HISTORY_ADDED", id, "clinicPatientId", clinicPatientId.toString());
        return jdbc.queryForObject("""
                SELECT mh.id, mh.category, mh.description, mh.recorded_at, u.full_name AS recorded_by
                FROM medical_history mh JOIN users u ON u.id = mh.recorded_by_user_id WHERE mh.id = ?
                """, (rs, row) -> new MedicalHistoryDto(rs.getObject("id", UUID.class), rs.getString("category"),
                rs.getString("description"), rs.getObject("recorded_at", OffsetDateTime.class), rs.getString("recorded_by")), id);
    }

    @Transactional
    public AllergyDto addAllergy(FirebasePrincipal principal, UUID clinicId, UUID clinicPatientId, AllergyRequest request) {
        UUID doctorUserId = userId(principal);
        requireDoctor(doctorUserId, clinicId);
        requireClinicPatient(clinicId, clinicPatientId);
        UUID id = jdbc.queryForObject("""
                INSERT INTO patient_allergies (clinic_id, clinic_patient_id, allergen, reaction, severity, notes, recorded_by_user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (clinic_id, clinic_patient_id, allergen)
                DO UPDATE SET reaction = EXCLUDED.reaction, severity = EXCLUDED.severity, notes = EXCLUDED.notes,
                              recorded_by_user_id = EXCLUDED.recorded_by_user_id
                RETURNING id
                """, UUID.class, clinicId, clinicPatientId, request.allergen().trim(), clean(request.reaction()),
                clean(request.severity()), clean(request.notes()), doctorUserId);
        audit(clinicId, doctorUserId, "PATIENT_ALLERGY_RECORDED", id, "clinicPatientId", clinicPatientId.toString());
        return jdbc.queryForObject("""
                SELECT id, allergen, reaction, severity, notes, created_at
                FROM patient_allergies WHERE id = ?
                """, (rs, row) -> new AllergyDto(rs.getObject("id", UUID.class), rs.getString("allergen"),
                rs.getString("reaction"), rs.getString("severity"), rs.getString("notes"),
                rs.getObject("created_at", OffsetDateTime.class)), id);
    }

    private UUID clinicPatientFor(UUID userId, UUID clinicId, boolean create) {
        Integer clinicExists = jdbc.queryForObject("SELECT COUNT(*) FROM clinics WHERE id = ? AND active = true", Integer.class, clinicId);
        if (clinicExists == null || clinicExists == 0) throw notFound("Clinic not found");
        Integer profileExists = jdbc.queryForObject("SELECT COUNT(*) FROM patient_profiles WHERE user_id = ?", Integer.class, userId);
        if (profileExists == null || profileExists == 0) throw new AccessDeniedException("A patient profile is required");
        UUID existing = jdbc.query("""
                SELECT cp.id FROM clinic_patients cp
                JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                JOIN clinics c ON c.id = cp.clinic_id AND c.active = true
                WHERE pp.user_id = ? AND cp.clinic_id = ? AND cp.active = true
                """, (rs, row) -> rs.getObject("id", UUID.class), userId, clinicId).stream().findFirst().orElse(null);
        if (existing != null || !create) return existing;
        String code = "P-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return jdbc.queryForObject("""
                INSERT INTO clinic_patients (clinic_id, patient_profile_id, patient_code)
                SELECT ?, pp.id, ? FROM patient_profiles pp
                JOIN clinics c ON c.id = ? AND c.active = true
                WHERE pp.user_id = ?
                ON CONFLICT (clinic_id, patient_profile_id) DO UPDATE SET patient_profile_id = EXCLUDED.patient_profile_id
                RETURNING id
                """, UUID.class, clinicId, code, clinicId, userId);
    }

    private FamilyMemberDto familyMember(UUID clinicId, UUID clinicPatientId, UUID id) {
        return jdbc.query("""
                SELECT id, full_name, relationship, date_of_birth, phone, authorized_by_patient
                FROM family_members WHERE id = ? AND clinic_id = ? AND clinic_patient_id = ?
                """, (rs, row) -> new FamilyMemberDto(rs.getObject("id", UUID.class), rs.getString("full_name"),
                rs.getString("relationship"), rs.getObject("date_of_birth", LocalDate.class), rs.getString("phone"),
                rs.getBoolean("authorized_by_patient")), id, clinicId, clinicPatientId).stream().findFirst()
                .orElseThrow(() -> notFound("Family member not found"));
    }

    private DoctorPatientDto doctorPatientRow(UUID clinicId, UUID clinicPatientId) {
        return jdbc.query("""
                SELECT cp.id AS clinic_patient_id, cp.patient_code, COALESCE(pp.preferred_name, u.full_name) AS full_name,
                       u.email, u.phone, COUNT(a.id) AS appointment_count, MAX(a.starts_at) AS last_appointment_at
                FROM clinic_patients cp JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                JOIN users u ON u.id = pp.user_id
                LEFT JOIN appointments a ON a.clinic_patient_id = cp.id AND a.clinic_id = cp.clinic_id
                WHERE cp.id = ? AND cp.clinic_id = ? AND cp.active = true
                GROUP BY cp.id, cp.patient_code, pp.preferred_name, u.full_name, u.email, u.phone
                """, (rs, row) -> new DoctorPatientDto(rs.getObject("clinic_patient_id", UUID.class),
                rs.getString("patient_code"), rs.getString("full_name"), rs.getString("email"), rs.getString("phone"),
                rs.getLong("appointment_count"), rs.getObject("last_appointment_at", OffsetDateTime.class)), clinicPatientId, clinicId)
                .stream().findFirst().orElseThrow(() -> notFound("Patient not found"));
    }

    private void requireClinicPatient(UUID clinicId, UUID clinicPatientId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM clinic_patients WHERE id = ? AND clinic_id = ? AND active = true",
                Integer.class, clinicPatientId, clinicId);
        if (count == null || count == 0) throw notFound("Patient not found");
    }

    private void requireDoctor(UUID userId, UUID clinicId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM clinic_members cm JOIN clinics c ON c.id = cm.clinic_id
                WHERE cm.user_id = ? AND cm.clinic_id = ? AND cm.role = 'DOCTOR' AND cm.active = true AND c.active = true
                """, Integer.class, userId, clinicId);
        if (count == null || count == 0) throw new AccessDeniedException("Doctor access is not enabled for this clinic");
    }

    private UUID userId(FirebasePrincipal principal) {
        if (principal == null || principal.uid() == null || principal.uid().isBlank()) throw new AccessDeniedException("Sign in is required");
        try { return jdbc.queryForObject("SELECT id FROM users WHERE firebase_uid = ? AND status = 'ACTIVE'", UUID.class, principal.uid()); }
        catch (EmptyResultDataAccessException exception) { throw new AccessDeniedException("Active account not found"); }
    }

    private void validateDate(LocalDate date) {
        if (date != null && (date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.of(1900, 1, 1)))) throw badRequest("Enter a valid date of birth");
    }

    private void audit(UUID clinicId, UUID actorId, String action, UUID entityId, String key, String value) {
        jdbc.update("""
                INSERT INTO audit_logs (clinic_id, actor_user_id, action, entity_type, entity_id, after_data)
                VALUES (?, ?, ?, 'patient', ?, jsonb_build_object(?, ?))
                """, clinicId, actorId, action, entityId, key, value);
    }

    private String clean(String value) { if (value == null) return null; String text = value.trim(); return text.isEmpty() ? null : text; }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
