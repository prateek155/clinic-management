package com.clinic.doctor;

import static com.clinic.doctor.DoctorSettingsDtos.*;

import com.clinic.security.FirebasePrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/doctor/settings")
public class DoctorSettingsController {
    private final DoctorSettingsService settings;

    public DoctorSettingsController(DoctorSettingsService settings) { this.settings = settings; }

    @GetMapping
    public SettingsDto get(@AuthenticationPrincipal FirebasePrincipal principal, @RequestParam UUID clinicId) {
        return settings.get(principal, clinicId);
    }

    @PutMapping
    public SettingsDto update(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @Valid @RequestBody SettingsRequest request) {
        return settings.update(principal, clinicId, request);
    }

    @GetMapping("/services")
    public List<ServiceDto> services(@AuthenticationPrincipal FirebasePrincipal principal, @RequestParam UUID clinicId) {
        return settings.services(principal, clinicId);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceDto addService(@AuthenticationPrincipal FirebasePrincipal principal, @RequestParam UUID clinicId,
            @Valid @RequestBody ServiceRequest request) {
        return settings.saveService(principal, clinicId, null, request);
    }

    @PutMapping("/services/{serviceId}")
    public ServiceDto updateService(@AuthenticationPrincipal FirebasePrincipal principal, @RequestParam UUID clinicId,
            @PathVariable UUID serviceId, @Valid @RequestBody ServiceRequest request) {
        return settings.saveService(principal, clinicId, serviceId, request);
    }

    @DeleteMapping("/services/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateService(@AuthenticationPrincipal FirebasePrincipal principal, @RequestParam UUID clinicId,
            @PathVariable UUID serviceId) {
        settings.deactivateService(principal, clinicId, serviceId);
    }
}
