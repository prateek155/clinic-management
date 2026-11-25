package com.clinic.consultation;

import static com.clinic.consultation.ConsultationDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.security.FirebasePrincipal;
import java.time.OffsetDateTime;
import java.util.List;
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
public class ConsultationService {
    private static final Set<String> FOOD_INSTRUCTIONS = Set.of("BEFORE_FOOD", "AFTER_FOOD", "WITH_FOOD", "ANYTIME");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ConsultationService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DoctorConsultationDto start(FirebasePrincipal principal, UUID clinicId, UUID appointmentId) {
        UUID userId = userId(principal);
        UUID doctorId = doctorProfileId(userId, clinicId);
        AppointmentContext appointment = appointment(clinicId, doctorId, appointmentId, true);
        int inserted = jdbc.update("""
                INSERT INTO consultations (clinic_id, appointment_id, clinic_patient_id, started_at, created_by_user_id)
                VALUES (?, ?, ?, now(), ?)
                ON CONFLICT (appointment_id) DO NOTHING
                """, clinicId, appointmentId, appointment.clinicPatientId(), userId);
        if (inserted == 1) audit(clinicId, userId, "CONSULTATION_STARTED", "consultation", appointmentId,
                "appointmentId", appointmentId);
        return doctorConsultation(principal, clinicId, appointmentId);
    }

    @Transactional
    public DoctorConsultationDto doctorConsultation(FirebasePrincipal principal, UUID clinicId, UUID appointmentId) {
        UUID userId = userId(principal);
        UUID doctorId = doctorProfileId(userId, clinicId);
        AppointmentContext appointment = appointment(clinicId, doctorId, appointmentId, false);
        ConsultRow row = consultationByAppointment(clinicId, appointmentId)
                .orElseThrow(() -> notFound("Consultation not found; start it from the appointment"));
        jdbc.update("""
                INSERT INTO audit_logs (clinic_id, actor_user_id, action, entity_type, entity_id, after_data)
                VALUES (?, ?, 'CONSULTATION_VIEWED', 'consultation', ?, jsonb_build_object('appointmentId', ?::text))
                """, clinicId, userId, row.id(), appointmentId);
        return consultationDto(row, appointment);
    }

    @Transactional
    public DoctorConsultationDto saveDraft(
            FirebasePrincipal principal, UUID clinicId, UUID consultationId, ConsultationRequest request) {
        UUID userId = userId(principal);
        UUID doctorId = doctorProfileId(userId, clinicId);
        ConsultRow current = consultation(clinicId, doctorId, consultationId, true);
        requireDraft(current);
        if (request.followUpDueAt() != null && !request.followUpDueAt().isAfter(OffsetDateTime.now())) {
            throw badRequest("Follow-up time must be in the future");
        }
        jdbc.update("""
                UPDATE consultations
                SET symptoms = ?, observations = ?, diagnosis = ?, treatment_plan = ?, doctor_notes = ?,
                    follow_up_instructions = ?, updated_at = now()
                WHERE id = ? AND clinic_id = ? AND finalized_at IS NULL
                """, clean(request.symptoms()), clean(request.observations()), clean(request.diagnosis()),
                clean(request.treatmentPlan()), clean(request.doctorNotes()), clean(request.followUpInstructions()),
                consultationId, clinicId);
        saveVitals(clinicId, consultationId, userId, request.vitals());
        saveFollowUp(clinicId, consultationId, request.followUpDueAt(), clean(request.followUpInstructions()));
        audit(clinicId, userId, "CONSULTATION_DRAFT_SAVED", "consultation", consultationId,
                "appointmentId", current.appointmentId());
        return doctorConsultation(principal, clinicId, current.appointmentId());
    }

    @Transactional
    public DoctorConsultationDto finalizeConsultation(FirebasePrincipal principal, UUID clinicId, UUID consultationId) {
        UUID userId = userId(principal);
        UUID doctorId = doctorProfileId(userId, clinicId);
        ConsultRow current = consultation(clinicId, doctorId, consultationId, true);
        requireDraft(current);
        AppointmentContext appointment = appointment(clinicId, doctorId, current.appointmentId(), true);
        if (!"IN_CONSULTATION".equals(appointment.status())) throw conflict("Only an active consultation can be finalized");
        jdbc.update("UPDATE consultations SET finalized_at = now(), updated_at = now() WHERE id = ? AND clinic_id = ? AND finalized_at IS NULL",
                consultationId, clinicId);
        jdbc.update("UPDATE appointments SET status = 'COMPLETED', updated_at = now() WHERE id = ? AND clinic_id = ? AND status = 'IN_CONSULTATION'",
                current.appointmentId(), clinicId);
        jdbc.update("""
                INSERT INTO appointment_status_history (clinic_id, appointment_id, previous_status, new_status, changed_by_user_id, reason)
                VALUES (?, ?, 'IN_CONSULTATION', 'COMPLETED', ?, 'Consultation finalized')
                """, clinicId, current.appointmentId(), userId);
        audit(clinicId, userId, "CONSULTATION_FINALIZED", "consultation", consultationId,
                "appointmentId", current.appointmentId());
        notifyPatient(current.clinicPatientId(), clinicId, "CONSULTATION", "Visit summary is available",
                "Your consultation has been completed and added to your medical records.");
        return doctorConsultation(principal, clinicId, current.appointmentId());
    }

    @Transactional
    public PrescriptionDto savePrescription(
            FirebasePrincipal principal, UUID clinicId, UUID consultationId, PrescriptionRequest request) {
        UUID userId = userId(principal);
        UUID doctorId = doctorProfileId(userId, clinicId);
        ConsultRow consult = consultation(clinicId, doctorId, consultationId, true);
        requireDraft(consult);
        UUID prescriptionId = jdbc.query("""
                SELECT id FROM prescriptions
                WHERE clinic_id = ? AND consultation_id = ? AND status <> 'VOID'
                ORDER BY created_at LIMIT 1 FOR UPDATE
                """, (rs, row) -> rs.getObject("id", UUID.class), clinicId, consultationId)
                .stream().findFirst().orElse(null);
        if (prescriptionId == null) {
            prescriptionId = jdbc.queryForObject("""
                    INSERT INTO prescriptions (clinic_id, consultation_id, clinic_patient_id, prescribed_by_user_id, instructions)
                    VALUES (?, ?, ?, ?, ?)
                    RETURNING id
                    """, UUID.class, clinicId, consultationId, consult.clinicPatientId(), userId, clean(request.instructions()));
        } else {
            String status = jdbc.queryForObject("SELECT status FROM prescriptions WHERE id = ? FOR UPDATE", String.class, prescriptionId);
            if (!"DRAFT".equals(status)) throw conflict("A finalized prescription cannot be edited");
            jdbc.update("UPDATE prescriptions SET instructions = ?, updated_at = now() WHERE id = ?",
                    clean(request.instructions()), prescriptionId);
            jdbc.update("DELETE FROM prescription_items WHERE prescription_id = ?", prescriptionId);
        }
        int position = 0;
        for (MedicineRequest item : request.items()) {
            validateMedicine(item);
            jdbc.update("""
                    INSERT INTO prescription_items
                        (clinic_id, prescription_id, medicine_name, generic_name, dosage, dose_unit, frequency,
                         duration, route, food_instruction, quantity, additional_instructions, starts_on, ends_on, position)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, clinicId, prescriptionId, item.medicineName().trim(), clean(item.genericName()),
                    item.dosage().trim(), clean(item.doseUnit()), item.frequency().trim(), item.duration().trim(),
                    clean(item.route()), clean(item.foodInstruction()), clean(item.quantity()),
                    clean(item.additionalInstructions()), item.startsOn(), item.endsOn(), position++);
        }
        audit(clinicId, userId, "PRESCRIPTION_DRAFT_SAVED", "prescription", prescriptionId,
                "consultationId", consultationId);
        return prescription(clinicId, consultationId);
    }

    @Transactional
    public PrescriptionDto finalizePrescription(FirebasePrincipal principal, UUID clinicId, UUID prescriptionId) {
        UUID userId = userId(principal);
        UUID doctorId = doctorProfileId(userId, clinicId);
        UUID consultationId = jdbc.query("""
                SELECT p.consultation_id FROM prescriptions p
                JOIN consultations c ON c.id = p.consultation_id AND c.clinic_id = p.clinic_id
                JOIN appointments a ON a.id = c.appointment_id AND a.clinic_id = c.clinic_id
                WHERE p.id = ? AND p.clinic_id = ? AND a.doctor_profile_id = ?
                FOR UPDATE OF p, c
                """, (rs, row) -> rs.getObject("consultation_id", UUID.class),
                prescriptionId, clinicId, doctorId).stream().findFirst().orElseThrow(() -> notFound("Prescription not found"));
        String status = jdbc.queryForObject("SELECT status FROM prescriptions WHERE id = ?", String.class, prescriptionId);
        if (!"DRAFT".equals(status)) throw conflict("Only a draft prescription can be finalized");
        Integer itemCount = jdbc.queryForObject("SELECT COUNT(*) FROM prescription_items WHERE prescription_id = ?", Integer.class, prescriptionId);
        if (itemCount == null || itemCount == 0) throw badRequest("Add at least one medicine before finalizing");
        jdbc.update("UPDATE prescriptions SET status = 'FINALIZED', finalized_at = now(), updated_at = now() WHERE id = ?", prescriptionId);
        audit(clinicId, userId, "PRESCRIPTION_FINALIZED", "prescription", prescriptionId,
                "consultationId", consultationId);
        UUID patientId = jdbc.queryForObject("SELECT clinic_patient_id FROM consultations WHERE id = ? AND clinic_id = ?",
                UUID.class, consultationId, clinicId);
        notifyPatient(patientId, clinicId, "PRESCRIPTION", "Prescription ready",
                "Your doctor finalized a prescription that is now available in medical records.");
        return prescription(clinicId, consultationId);
    }

    @Transactional
    public PrescriptionDto amendPrescription(FirebasePrincipal principal, UUID clinicId, UUID prescriptionId,
            PrescriptionAmendmentRequest request) {
        UUID userId = userId(principal);
        UUID doctorId = doctorProfileId(userId, clinicId);
        PrescriptionContext context = jdbc.query("""
                SELECT p.consultation_id, p.clinic_patient_id, p.status, p.instructions
                FROM prescriptions p
                JOIN consultations c ON c.id = p.consultation_id AND c.clinic_id = p.clinic_id
                JOIN appointments a ON a.id = c.appointment_id AND a.clinic_id = c.clinic_id
                WHERE p.id = ? AND p.clinic_id = ? AND a.doctor_profile_id = ?
                FOR UPDATE OF p
                """, (rs, row) -> new PrescriptionContext(rs.getObject("consultation_id", UUID.class),
                rs.getObject("clinic_patient_id", UUID.class), rs.getString("status"), rs.getString("instructions")),
                prescriptionId, clinicId, doctorId).stream().findFirst()
                .orElseThrow(() -> notFound("Prescription not found"));
        if (!"FINALIZED".equals(context.status())) throw conflict("Only a finalized prescription can be amended");
        List<MedicineRequest> requestedItems = request.changes().items();
        requestedItems.forEach(this::validateMedicine);
        PrescriptionContents amended = new PrescriptionContents(clean(request.changes().instructions()),
                requestedItems.stream().map(this::prescriptionItem).toList());
        String priorJson = jdbc.query("""
                SELECT amended_contents::text FROM prescription_amendments
                WHERE clinic_id = ? AND prescription_id = ? ORDER BY created_at DESC LIMIT 1
                """, (rs, row) -> rs.getString(1), clinicId, prescriptionId).stream().findFirst()
                .orElseGet(() -> json(new PrescriptionContents(context.instructions(), prescriptionItems(prescriptionId))));
        String amendedJson = json(amended);
        UUID amendmentId = jdbc.queryForObject("""
                INSERT INTO prescription_amendments
                    (clinic_id, prescription_id, authored_by_user_id, reason, prior_contents, amended_contents)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb) RETURNING id
                """, UUID.class, clinicId, prescriptionId, userId, request.reason().trim(), priorJson, amendedJson);
        audit(clinicId, userId, "PRESCRIPTION_AMENDED", "prescription", prescriptionId,
                "amendmentId", amendmentId);
        notifyPatient(context.clinicPatientId(), clinicId, "PRESCRIPTION", "Prescription amended",
                "Your doctor added a documented amendment to a finalized prescription. Review its history in medical records.");
        return prescription(clinicId, context.consultationId());
    }

    @Transactional(readOnly = true)
    public PrescriptionDto doctorPrescription(FirebasePrincipal principal, UUID clinicId, UUID consultationId) {
        UUID doctorId = doctorProfileId(userId(principal), clinicId);
        consultation(clinicId, doctorId, consultationId, false);
        return prescription(clinicId, consultationId);
    }

    @Transactional(readOnly = true)
    public List<PatientConsultationDto> patientConsultations(FirebasePrincipal principal) {
        UUID userId = userId(principal);
        List<PatientConsultationDto> result = jdbc.query("""
                SELECT c.id, cl.name AS clinic_name, du.full_name AS doctor_name, c.started_at, c.finalized_at,
                       c.symptoms, c.observations, c.diagnosis, c.treatment_plan, c.follow_up_instructions,
                       (SELECT f.due_at FROM follow_ups f WHERE f.consultation_id = c.id AND f.status = 'OPEN' ORDER BY f.created_at DESC LIMIT 1) AS follow_up_due_at,
                       v.height_cm, v.weight_kg, v.temperature_c, v.systolic_bp, v.diastolic_bp, v.pulse_bpm, v.oxygen_saturation,
                       a.clinic_id
                FROM consultations c
                JOIN clinic_patients cp ON cp.id = c.clinic_patient_id AND cp.clinic_id = c.clinic_id
                JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                JOIN appointments a ON a.id = c.appointment_id AND a.clinic_id = c.clinic_id
                JOIN clinics cl ON cl.id = c.clinic_id
                JOIN doctor_profiles dp ON dp.id = a.doctor_profile_id AND dp.clinic_id = a.clinic_id
                JOIN clinic_members dcm ON dcm.id = dp.clinic_member_id
                JOIN users du ON du.id = dcm.user_id
                LEFT JOIN patient_vitals v ON v.consultation_id = c.id AND v.clinic_id = c.clinic_id
                WHERE pp.user_id = ? AND c.finalized_at IS NOT NULL
                ORDER BY c.finalized_at DESC LIMIT 200
                """, (rs, row) -> new PatientConsultationDto(
                rs.getObject("id", UUID.class), rs.getString("clinic_name"), rs.getString("doctor_name"),
                rs.getObject("started_at", OffsetDateTime.class), rs.getObject("finalized_at", OffsetDateTime.class),
                rs.getString("symptoms"), rs.getString("observations"), rs.getString("diagnosis"),
                rs.getString("treatment_plan"), rs.getString("follow_up_instructions"),
                rs.getObject("follow_up_due_at", OffsetDateTime.class), vitalsFrom(rs)), userId);
        return result;
    }

    @Transactional(readOnly = true)
    public List<PatientPrescriptionDto> patientPrescriptions(FirebasePrincipal principal) {
        UUID userId = userId(principal);
        List<PatientPrescriptionDto> summaries = jdbc.query("""
                SELECT p.id, p.consultation_id, cl.name AS clinic_name, du.full_name AS doctor_name,
                       p.status, p.instructions, p.created_at, p.finalized_at
                FROM prescriptions p
                JOIN consultations c ON c.id = p.consultation_id AND c.clinic_id = p.clinic_id
                JOIN clinic_patients cp ON cp.id = p.clinic_patient_id AND cp.clinic_id = p.clinic_id
                JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                JOIN clinics cl ON cl.id = p.clinic_id
                JOIN appointments a ON a.id = c.appointment_id AND a.clinic_id = c.clinic_id
                JOIN doctor_profiles dp ON dp.id = a.doctor_profile_id AND dp.clinic_id = a.clinic_id
                JOIN clinic_members dcm ON dcm.id = dp.clinic_member_id
                JOIN users du ON du.id = dcm.user_id
                WHERE pp.user_id = ? AND p.status = 'FINALIZED'
                ORDER BY p.finalized_at DESC LIMIT 200
                """, (rs, row) -> new PatientPrescriptionDto(
                rs.getObject("id", UUID.class), rs.getString("clinic_name"), rs.getString("doctor_name"),
                rs.getString("status"), rs.getString("instructions"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("finalized_at", OffsetDateTime.class), List.of(), List.of()), userId);
        return summaries.stream().map(item -> new PatientPrescriptionDto(item.id(), item.clinicName(), item.doctorName(),
                item.status(), item.instructions(), item.createdAt(), item.finalizedAt(), prescriptionItems(item.id()),
                prescriptionAmendments(item.id()))).toList();
    }

    private void saveVitals(UUID clinicId, UUID consultationId, UUID userId, VitalsRequest vitals) {
        if (vitals == null) return;
        boolean empty = vitals.heightCm() == null && vitals.weightKg() == null && vitals.temperatureC() == null
                && vitals.systolicBp() == null && vitals.diastolicBp() == null && vitals.pulseBpm() == null
                && vitals.oxygenSaturation() == null;
        if (empty) return;
        jdbc.update("""
                INSERT INTO patient_vitals
                    (clinic_id, consultation_id, height_cm, weight_kg, temperature_c, systolic_bp, diastolic_bp,
                     pulse_bpm, oxygen_saturation, recorded_by_user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (consultation_id) DO UPDATE SET
                    height_cm = EXCLUDED.height_cm, weight_kg = EXCLUDED.weight_kg,
                    temperature_c = EXCLUDED.temperature_c, systolic_bp = EXCLUDED.systolic_bp,
                    diastolic_bp = EXCLUDED.diastolic_bp, pulse_bpm = EXCLUDED.pulse_bpm,
                    oxygen_saturation = EXCLUDED.oxygen_saturation, recorded_by_user_id = EXCLUDED.recorded_by_user_id,
                    recorded_at = now()
                """, clinicId, consultationId, vitals.heightCm(), vitals.weightKg(), vitals.temperatureC(),
                vitals.systolicBp(), vitals.diastolicBp(), vitals.pulseBpm(), vitals.oxygenSaturation(), userId);
    }

    private void saveFollowUp(UUID clinicId, UUID consultationId, OffsetDateTime dueAt, String instructions) {
        if (dueAt == null) return;
        Integer updated = jdbc.update("""
                UPDATE follow_ups SET due_at = ?, instructions = ?
                WHERE clinic_id = ? AND consultation_id = ? AND status = 'OPEN'
                """, dueAt, instructions, clinicId, consultationId);
        if (updated == 0) jdbc.update("""
                INSERT INTO follow_ups (clinic_id, consultation_id, due_at, instructions)
                VALUES (?, ?, ?, ?)
                """, clinicId, consultationId, dueAt, instructions);
    }

    private void validateMedicine(MedicineRequest item) {
        if (item.foodInstruction() != null && !FOOD_INSTRUCTIONS.contains(item.foodInstruction())) {
            throw badRequest("Food instruction must be BEFORE_FOOD, AFTER_FOOD, WITH_FOOD, or ANYTIME");
        }
        if (item.startsOn() != null && item.endsOn() != null && item.endsOn().isBefore(item.startsOn())) {
            throw badRequest("Medicine end date cannot be before its start date");
        }
    }

    private DoctorConsultationDto consultationDto(ConsultRow row, AppointmentContext appointment) {
        return new DoctorConsultationDto(row.id(), row.appointmentId(), row.clinicPatientId(), appointment.patientName(),
                appointment.patientCode(), row.symptoms(), row.observations(), row.diagnosis(), row.treatmentPlan(),
                row.doctorNotes(), row.followUpInstructions(), row.startedAt(), row.finalizedAt(),
                consultationVitals(row.id()), followUpDueAt(row.id()), followUpStatus(row.id()));
    }

    private VitalsRequest consultationVitals(UUID consultationId) {
        return jdbc.query("""
                SELECT height_cm, weight_kg, temperature_c, systolic_bp, diastolic_bp, pulse_bpm, oxygen_saturation
                FROM patient_vitals WHERE consultation_id = ?
                """, (rs, row) -> vitalsFrom(rs), consultationId).stream().findFirst().orElse(null);
    }

    private VitalsRequest vitalsFrom(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new VitalsRequest(rs.getBigDecimal("height_cm"), rs.getBigDecimal("weight_kg"),
                rs.getBigDecimal("temperature_c"), rs.getObject("systolic_bp", Integer.class),
                rs.getObject("diastolic_bp", Integer.class), rs.getObject("pulse_bpm", Integer.class),
                rs.getBigDecimal("oxygen_saturation"));
    }

    private OffsetDateTime followUpDueAt(UUID consultationId) {
        return jdbc.query("SELECT due_at FROM follow_ups WHERE consultation_id = ? AND status = 'OPEN' ORDER BY created_at DESC LIMIT 1",
                (rs, row) -> rs.getObject("due_at", OffsetDateTime.class), consultationId).stream().findFirst().orElse(null);
    }

    private String followUpStatus(UUID consultationId) {
        return jdbc.query("SELECT status FROM follow_ups WHERE consultation_id = ? ORDER BY created_at DESC LIMIT 1",
                (rs, row) -> rs.getString("status"), consultationId).stream().findFirst().orElse(null);
    }

    private java.util.Optional<ConsultRow> consultationByAppointment(UUID clinicId, UUID appointmentId) {
        return jdbc.query("""
                SELECT id, appointment_id, clinic_patient_id, symptoms, observations, diagnosis, treatment_plan,
                       doctor_notes, follow_up_instructions, started_at, finalized_at
                FROM consultations WHERE clinic_id = ? AND appointment_id = ?
                """, (rs, row) -> new ConsultRow(rs.getObject("id", UUID.class), rs.getObject("appointment_id", UUID.class),
                rs.getObject("clinic_patient_id", UUID.class), rs.getString("symptoms"), rs.getString("observations"),
                rs.getString("diagnosis"), rs.getString("treatment_plan"), rs.getString("doctor_notes"),
                rs.getString("follow_up_instructions"), rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finalized_at", OffsetDateTime.class)), clinicId, appointmentId).stream().findFirst();
    }

    private ConsultRow consultation(UUID clinicId, UUID doctorId, UUID consultationId, boolean lock) {
        String sql = """
                SELECT c.id, c.appointment_id, c.clinic_patient_id, c.symptoms, c.observations, c.diagnosis,
                       c.treatment_plan, c.doctor_notes, c.follow_up_instructions, c.started_at, c.finalized_at
                FROM consultations c
                JOIN appointments a ON a.id = c.appointment_id AND a.clinic_id = c.clinic_id
                WHERE c.id = ? AND c.clinic_id = ? AND a.doctor_profile_id = ?
                """ + (lock ? " FOR UPDATE OF c" : "");
        return jdbc.query(sql, (rs, row) -> new ConsultRow(rs.getObject("id", UUID.class),
                rs.getObject("appointment_id", UUID.class), rs.getObject("clinic_patient_id", UUID.class),
                rs.getString("symptoms"), rs.getString("observations"), rs.getString("diagnosis"),
                rs.getString("treatment_plan"), rs.getString("doctor_notes"), rs.getString("follow_up_instructions"),
                rs.getObject("started_at", OffsetDateTime.class), rs.getObject("finalized_at", OffsetDateTime.class)),
                consultationId, clinicId, doctorId).stream().findFirst().orElseThrow(() -> notFound("Consultation not found"));
    }

    private AppointmentContext appointment(UUID clinicId, UUID doctorId, UUID appointmentId, boolean requireInProgress) {
        AppointmentContext result = jdbc.query("""
                SELECT a.clinic_patient_id, a.status, cp.patient_code,
                       COALESCE(pp.preferred_name, u.full_name) AS patient_name
                FROM appointments a
                JOIN clinic_patients cp ON cp.id = a.clinic_patient_id AND cp.clinic_id = a.clinic_id
                JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                JOIN users u ON u.id = pp.user_id
                WHERE a.id = ? AND a.clinic_id = ? AND a.doctor_profile_id = ?
                """, (rs, row) -> new AppointmentContext(rs.getObject("clinic_patient_id", UUID.class),
                rs.getString("status"), rs.getString("patient_code"), rs.getString("patient_name")),
                appointmentId, clinicId, doctorId).stream().findFirst()
                .orElseThrow(() -> notFound("Appointment not found"));
        if (requireInProgress && !"IN_CONSULTATION".equals(result.status())) {
            throw conflict("Mark the appointment as in consultation before opening or finalizing a consultation");
        }
        return result;
    }

    private PrescriptionDto prescription(UUID clinicId, UUID consultationId) {
        PrescriptionHeader header = jdbc.query("""
                SELECT id, consultation_id, status, instructions, created_at, finalized_at
                FROM prescriptions WHERE clinic_id = ? AND consultation_id = ? AND status <> 'VOID'
                ORDER BY created_at DESC LIMIT 1
                """, (rs, row) -> new PrescriptionHeader(rs.getObject("id", UUID.class),
                rs.getObject("consultation_id", UUID.class), rs.getString("status"), rs.getString("instructions"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("finalized_at", OffsetDateTime.class)),
                clinicId, consultationId).stream().findFirst().orElseThrow(() -> notFound("Prescription not found"));
        return new PrescriptionDto(header.id(), header.consultationId(), header.status(), header.instructions(),
                header.createdAt(), header.finalizedAt(), prescriptionItems(header.id()), prescriptionAmendments(header.id()));
    }

    private List<PrescriptionAmendmentDto> prescriptionAmendments(UUID prescriptionId) {
        List<AmendmentRow> amendments = jdbc.query("""
                SELECT id, reason, amended_contents::text AS amended_contents, created_at
                FROM prescription_amendments WHERE prescription_id = ? ORDER BY created_at, id
                """, (rs, row) -> new AmendmentRow(rs.getObject("id", UUID.class), rs.getString("reason"),
                rs.getString("amended_contents"), rs.getObject("created_at", OffsetDateTime.class)), prescriptionId);
        return amendments.stream().map(item -> {
            try {
                PrescriptionContents contents = objectMapper.readValue(item.contents(), PrescriptionContents.class);
                return new PrescriptionAmendmentDto(item.id(), item.reason(), item.createdAt(),
                        contents.instructions(), contents.items());
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Stored prescription amendment is invalid", exception);
            }
        }).toList();
    }

    private PrescriptionItemDto prescriptionItem(MedicineRequest item) {
        return new PrescriptionItemDto(null, item.medicineName().trim(), clean(item.genericName()), item.dosage().trim(),
                clean(item.doseUnit()), item.frequency().trim(), item.duration().trim(), clean(item.route()),
                clean(item.foodInstruction()), clean(item.quantity()), clean(item.additionalInstructions()),
                item.startsOn(), item.endsOn());
    }

    private String json(PrescriptionContents contents) {
        try { return objectMapper.writeValueAsString(contents); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not record prescription amendment", exception); }
    }

    private List<PrescriptionItemDto> prescriptionItems(UUID prescriptionId) {
        return jdbc.query("""
                SELECT id, medicine_name, generic_name, dosage, dose_unit, frequency, duration, route,
                       food_instruction, quantity, additional_instructions, starts_on, ends_on
                FROM prescription_items WHERE prescription_id = ? ORDER BY position
                """, (rs, row) -> new PrescriptionItemDto(rs.getObject("id", UUID.class), rs.getString("medicine_name"),
                rs.getString("generic_name"), rs.getString("dosage"), rs.getString("dose_unit"), rs.getString("frequency"),
                rs.getString("duration"), rs.getString("route"), rs.getString("food_instruction"), rs.getString("quantity"),
                rs.getString("additional_instructions"), rs.getObject("starts_on", java.time.LocalDate.class),
                rs.getObject("ends_on", java.time.LocalDate.class)), prescriptionId);
    }

    private void requireDraft(ConsultRow row) {
        if (row.finalizedAt() != null) throw conflict("Finalized consultations are read-only");
    }

    private void audit(UUID clinicId, UUID userId, String action, String type, UUID entityId, String key, UUID value) {
        jdbc.update("""
                INSERT INTO audit_logs (clinic_id, actor_user_id, action, entity_type, entity_id, after_data)
                VALUES (?, ?, ?, ?, ?, jsonb_build_object(?, ?::text))
                """, clinicId, userId, action, type, entityId, key, value);
    }

    private void notifyPatient(UUID clinicPatientId, UUID clinicId, String category, String title, String body) {
        UUID patientUser = jdbc.queryForObject("""
                SELECT pp.user_id FROM clinic_patients cp JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                WHERE cp.id = ? AND cp.clinic_id = ?
                """, UUID.class, clinicPatientId, clinicId);
        jdbc.update("INSERT INTO notifications (clinic_id, user_id, category, title, body) VALUES (?, ?, ?, ?, ?)",
                clinicId, patientUser, category, title, body);
    }

    private UUID userId(FirebasePrincipal principal) {
        if (principal == null || principal.uid() == null || principal.uid().isBlank()) throw new AccessDeniedException("Sign in is required");
        try {
            return jdbc.queryForObject("SELECT id FROM users WHERE firebase_uid = ? AND status = 'ACTIVE'", UUID.class, principal.uid());
        } catch (EmptyResultDataAccessException exception) {
            throw new AccessDeniedException("Active account not found");
        }
    }

    private UUID doctorProfileId(UUID userId, UUID clinicId) {
        return jdbc.query("""
                SELECT dp.id FROM doctor_profiles dp
                JOIN clinic_members cm ON cm.id = dp.clinic_member_id AND cm.clinic_id = dp.clinic_id
                JOIN clinics c ON c.id = dp.clinic_id
                WHERE cm.user_id = ? AND dp.clinic_id = ? AND cm.role = 'DOCTOR'
                  AND cm.active = true AND dp.active = true AND c.active = true
                """, (rs, row) -> rs.getObject("id", UUID.class), userId, clinicId).stream().findFirst()
                .orElseThrow(() -> new AccessDeniedException("Doctor access is not enabled for this clinic"));
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    private record AppointmentContext(UUID clinicPatientId, String status, String patientCode, String patientName) {}
    private record ConsultRow(UUID id, UUID appointmentId, UUID clinicPatientId, String symptoms, String observations,
            String diagnosis, String treatmentPlan, String doctorNotes, String followUpInstructions,
            OffsetDateTime startedAt, OffsetDateTime finalizedAt) {}
    private record PrescriptionHeader(UUID id, UUID consultationId, String status, String instructions,
            OffsetDateTime createdAt, OffsetDateTime finalizedAt) {}
    private record PrescriptionContext(UUID consultationId, UUID clinicPatientId, String status, String instructions) {}
    private record AmendmentRow(UUID id, String reason, String contents, OffsetDateTime createdAt) {}
}
