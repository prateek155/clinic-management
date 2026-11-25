package com.clinic.medicalrecord;

import static com.clinic.medicalrecord.MedicalReportDtos.*;

import com.clinic.security.FirebasePrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class MedicalReportController {
    private final MedicalReportService reports;

    public MedicalReportController(MedicalReportService reports) { this.reports = reports; }

    @PostMapping(value = "/patient/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReportDto uploadPatient(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @RequestParam String category,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) UUID consultationId,
            @RequestParam("file") MultipartFile file) {
        return reports.uploadForPatient(principal, clinicId, category, description, consultationId, file);
    }

    @GetMapping("/patient/reports")
    public List<ReportDto> patientReports(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId) { return reports.patientReports(principal, clinicId); }

    @GetMapping("/patient/reports/{reportId}/download")
    public ReportDownloadDto patientDownload(@AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable UUID reportId) { return reports.patientDownload(principal, reportId); }

    @PostMapping(value = "/doctor/patients/{clinicPatientId}/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReportDto uploadDoctor(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID clinicPatientId,
            @RequestParam String category, @RequestParam(required = false) String description,
            @RequestParam(required = false) UUID consultationId, @RequestParam("file") MultipartFile file) {
        return reports.uploadForDoctor(principal, clinicId, clinicPatientId, category, description, consultationId, file);
    }

    @GetMapping("/doctor/patients/{clinicPatientId}/reports")
    public List<ReportDto> doctorReports(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID clinicPatientId) {
        return reports.doctorReports(principal, clinicId, clinicPatientId);
    }

    @GetMapping("/doctor/reports/{reportId}/download")
    public ReportDownloadDto doctorDownload(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID reportId) {
        return reports.doctorDownload(principal, clinicId, reportId);
    }
}
