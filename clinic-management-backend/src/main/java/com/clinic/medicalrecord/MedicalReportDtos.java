package com.clinic.medicalrecord;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class MedicalReportDtos {
    private MedicalReportDtos() {}
    public record ReportDto(UUID id, UUID clinicId, UUID clinicPatientId, UUID consultationId,
            String category, String description, String originalFilename, String contentType,
            long sizeBytes, OffsetDateTime createdAt) {}
    public record ReportDownloadDto(String url, String originalFilename, String contentType, OffsetDateTime expiresAt) {}
}
