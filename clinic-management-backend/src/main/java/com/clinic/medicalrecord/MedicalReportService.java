package com.clinic.medicalrecord;

import static com.clinic.medicalrecord.MedicalReportDtos.*;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.firebase.cloud.StorageClient;
import com.google.firebase.FirebaseApp;
import com.clinic.security.FirebasePrincipal;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MedicalReportService {
    private static final long MAX_SIZE = 50L * 1024 * 1024;
    private static final Set<String> CATEGORIES = Set.of("LAB", "IMAGING", "XRAY", "BLOOD_TEST", "OTHER");
    private static final Set<String> CONTENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");
    private final JdbcTemplate jdbc;
    private final ObjectProvider<StorageClient> storageClients;
    private final String bucketName;

    public MedicalReportService(JdbcTemplate jdbc, ObjectProvider<StorageClient> storageClients,
            @Value("${firebase.storage.bucket:}") String bucketName) {
        this.jdbc = jdbc;
        this.storageClients = storageClients;
        this.bucketName = normalizeBucket(bucketName);
    }

    @Transactional
    public ReportDto uploadForPatient(FirebasePrincipal principal, UUID clinicId, String category,
            String description, UUID consultationId, MultipartFile file) {
        UUID userId = userId(principal);
        UUID patientId = patientProfileId(userId);
        UUID clinicPatientId = ensureClinicPatient(clinicId, patientId);
        if (consultationId != null) requireOwnedConsultation(consultationId, clinicId, clinicPatientId);
        return upload(userId, clinicId, clinicPatientId, consultationId, category, description, file);
    }

    @Transactional(readOnly = true)
    public List<ReportDto> patientReports(FirebasePrincipal principal, UUID clinicId) {
        UUID userId = userId(principal);
        patientProfileId(userId);
        return jdbc.query("""
                SELECT mr.id, mr.clinic_id, mr.clinic_patient_id, mr.consultation_id, mr.category, mr.description,
                       mr.original_filename, mr.content_type, mr.size_bytes, mr.created_at
                FROM medical_reports mr
                JOIN clinic_patients cp ON cp.id = mr.clinic_patient_id AND cp.clinic_id = mr.clinic_id
                JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                WHERE pp.user_id = ? AND mr.clinic_id = ?
                ORDER BY mr.created_at DESC LIMIT 200
                """, this::mapReport, userId, clinicId);
    }

    @Transactional
    public ReportDownloadDto patientDownload(FirebasePrincipal principal, UUID reportId) {
        UUID userId = userId(principal);
        ReportRow row = jdbc.query("""
                SELECT mr.id, mr.clinic_id, mr.clinic_patient_id, mr.consultation_id, mr.category, mr.description,
                       mr.original_filename, mr.content_type, mr.size_bytes, mr.created_at, mr.storage_key
                FROM medical_reports mr
                JOIN clinic_patients cp ON cp.id = mr.clinic_patient_id AND cp.clinic_id = mr.clinic_id
                JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                WHERE mr.id = ? AND pp.user_id = ?
                """, this::mapReportRow, reportId, userId).stream().findFirst()
                .orElseThrow(() -> notFound("Medical report not found"));
        audit(row.clinicId(), userId, "MEDICAL_REPORT_DOWNLOADED", reportId);
        return signedDownload(row);
    }

    @Transactional
    public ReportDto uploadForDoctor(FirebasePrincipal principal, UUID clinicId, UUID clinicPatientId,
            String category, String description, UUID consultationId, MultipartFile file) {
        UUID userId = userId(principal);
        requireDoctor(userId, clinicId);
        requireClinicPatient(clinicId, clinicPatientId);
        if (consultationId != null) requireOwnedConsultation(consultationId, clinicId, clinicPatientId);
        return upload(userId, clinicId, clinicPatientId, consultationId, category, description, file);
    }

    @Transactional(readOnly = true)
    public List<ReportDto> doctorReports(FirebasePrincipal principal, UUID clinicId, UUID clinicPatientId) {
        requireDoctor(userId(principal), clinicId);
        requireClinicPatient(clinicId, clinicPatientId);
        return jdbc.query("""
                SELECT id, clinic_id, clinic_patient_id, consultation_id, category, description,
                       original_filename, content_type, size_bytes, created_at
                FROM medical_reports WHERE clinic_id = ? AND clinic_patient_id = ?
                ORDER BY created_at DESC LIMIT 200
                """, this::mapReport, clinicId, clinicPatientId);
    }

    @Transactional
    public ReportDownloadDto doctorDownload(FirebasePrincipal principal, UUID clinicId, UUID reportId) {
        UUID userId = userId(principal);
        requireDoctor(userId, clinicId);
        ReportRow row = jdbc.query("""
                SELECT id, clinic_id, clinic_patient_id, consultation_id, category, description,
                       original_filename, content_type, size_bytes, created_at, storage_key
                FROM medical_reports WHERE id = ? AND clinic_id = ?
                """, this::mapReportRow, reportId, clinicId).stream().findFirst()
                .orElseThrow(() -> notFound("Medical report not found"));
        audit(clinicId, userId, "MEDICAL_REPORT_DOWNLOADED", reportId);
        return signedDownload(row);
    }

    private ReportDto upload(UUID actorId, UUID clinicId, UUID clinicPatientId, UUID consultationId,
            String requestedCategory, String description, MultipartFile file) {
        String category = requestedCategory == null ? "" : requestedCategory.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) throw badRequest("Choose a supported report category");
        validateFile(file);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).trim();
        String filename = safeFilename(file.getOriginalFilename());
        String storageKey = "clinics/" + clinicId + "/patients/" + clinicPatientId + "/" + UUID.randomUUID();
        Bucket bucket = bucket();
        try (InputStream input = file.getInputStream()) {
            bucket.create(storageKey, input, contentType);
        } catch (IOException | RuntimeException exception) {
            throw storageUnavailable(exception);
        }
        try {
            UUID id = jdbc.queryForObject("""
                    INSERT INTO medical_reports
                        (clinic_id, clinic_patient_id, consultation_id, uploaded_by_user_id, category, description,
                         original_filename, content_type, size_bytes, storage_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, UUID.class, clinicId, clinicPatientId, consultationId, actorId, category,
                    clean(description), filename, contentType, file.getSize(), storageKey);
            audit(clinicId, actorId, "MEDICAL_REPORT_UPLOADED", id);
            return jdbc.queryForObject("""
                    SELECT id, clinic_id, clinic_patient_id, consultation_id, category, description,
                           original_filename, content_type, size_bytes, created_at
                    FROM medical_reports WHERE id = ?
                    """, this::mapReport, id);
        } catch (RuntimeException exception) {
            try { bucket.get(storageKey).delete(); } catch (RuntimeException ignored) { }
            throw exception;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() < 1 || file.getSize() > MAX_SIZE) {
            throw badRequest("Choose a report file up to 50 MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).trim();
        if (!CONTENT_TYPES.contains(contentType)) throw badRequest("Allowed report formats are PDF, JPEG, PNG, and WebP");
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            boolean valid = switch (contentType) {
                case "application/pdf" -> header.length >= 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
                case "image/jpeg" -> header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
                case "image/png" -> header.length >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
                case "image/webp" -> header.length >= 12 && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                        && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
                default -> false;
            };
            if (!valid) throw badRequest("The file contents do not match the selected file type");
        } catch (IOException exception) {
            throw badRequest("The uploaded report could not be read");
        }
    }

    private ReportDownloadDto signedDownload(ReportRow row) {
        try {
            Blob blob = bucket().get(row.storageKey());
            if (blob == null) throw notFound("Report file is missing from private storage");
            OffsetDateTime expires = OffsetDateTime.now().plusMinutes(5);
            String url = blob.signUrl(5, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature()).toString();
            return new ReportDownloadDto(url, row.report().originalFilename(), row.report().contentType(), expires);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw storageUnavailable(exception);
        }
    }

    private Bucket bucket() {
        if (bucketName.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Private report storage is not configured");
        StorageClient client = storageClients.getIfAvailable();
        if (client == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Firebase report storage is not configured");
        try { return client.bucket(bucketName); }
        catch (RuntimeException exception) { throw storageUnavailable(exception); }
    }

    private ResponseStatusException storageUnavailable(Throwable cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Private report storage could not be reached or signed. Check the Firebase Storage bucket and service account permissions.", cause);
    }

    private ReportDto mapReport(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ReportDto(rs.getObject("id", UUID.class), rs.getObject("clinic_id", UUID.class),
                rs.getObject("clinic_patient_id", UUID.class), rs.getObject("consultation_id", UUID.class),
                rs.getString("category"), rs.getString("description"), rs.getString("original_filename"),
                rs.getString("content_type"), rs.getLong("size_bytes"), rs.getObject("created_at", OffsetDateTime.class));
    }

    private ReportRow mapReportRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        ReportDto dto = mapReport(rs, row);
        return new ReportRow(dto, rs.getString("storage_key"));
    }

    private UUID ensureClinicPatient(UUID clinicId, UUID patientProfileId) {
        String code = "P-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        try {
            return jdbc.queryForObject("""
                    INSERT INTO clinic_patients (clinic_id, patient_profile_id, patient_code)
                    SELECT ?, ?, ? WHERE EXISTS (SELECT 1 FROM clinics WHERE id = ? AND active = true)
                    ON CONFLICT (clinic_id, patient_profile_id) DO UPDATE SET patient_profile_id = EXCLUDED.patient_profile_id
                    RETURNING id
                    """, UUID.class, clinicId, patientProfileId, code, clinicId);
        } catch (EmptyResultDataAccessException exception) { throw notFound("Clinic not found"); }
    }

    private void requireOwnedConsultation(UUID consultationId, UUID clinicId, UUID clinicPatientId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM consultations WHERE id = ? AND clinic_id = ? AND clinic_patient_id = ?
                """, Integer.class, consultationId, clinicId, clinicPatientId);
        if (count == null || count == 0) throw notFound("Consultation not found for this patient");
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

    private UUID patientProfileId(UUID userId) {
        return jdbc.query("SELECT id FROM patient_profiles WHERE user_id = ?", (rs, row) -> rs.getObject("id", UUID.class), userId)
                .stream().findFirst().orElseThrow(() -> new AccessDeniedException("A patient profile is required"));
    }

    private UUID userId(FirebasePrincipal principal) {
        if (principal == null || principal.uid() == null || principal.uid().isBlank()) throw new AccessDeniedException("Sign in is required");
        try { return jdbc.queryForObject("SELECT id FROM users WHERE firebase_uid = ? AND status = 'ACTIVE'", UUID.class, principal.uid()); }
        catch (EmptyResultDataAccessException exception) { throw new AccessDeniedException("Active account not found"); }
    }

    private void audit(UUID clinicId, UUID userId, String action, UUID reportId) {
        jdbc.update("""
                INSERT INTO audit_logs (clinic_id, actor_user_id, action, entity_type, entity_id)
                VALUES (?, ?, ?, 'medical_report', ?)
                """, clinicId, userId, action, reportId);
    }

    private String safeFilename(String original) {
        if (original == null || original.isBlank()) return "medical-report";
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank()) return "medical-report";
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private String clean(String value) { if (value == null) return null; String text = value.trim(); return text.isEmpty() ? null : text; }
    private static String normalizeBucket(String value) { return value == null ? "" : value.trim().replaceFirst("^gs://", "").replaceFirst("/$", ""); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    private record ReportRow(ReportDto report, String storageKey) {
        UUID clinicId() { return report.clinicId(); }
    }
}
