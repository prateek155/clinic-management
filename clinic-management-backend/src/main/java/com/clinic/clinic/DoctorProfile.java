package com.clinic.clinic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doctor_profiles")
public class DoctorProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "clinic_member_id", nullable = false, unique = true)
    private UUID clinicMemberId;

    @Column(name = "consultation_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal consultationFee = BigDecimal.ZERO;

    @Column(name = "consultation_duration_minutes", nullable = false)
    private int consultationDurationMinutes = 20;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected DoctorProfile() {}

    public DoctorProfile(UUID clinicId, UUID clinicMemberId) {
        this.clinicId = clinicId;
        this.clinicMemberId = clinicMemberId;
    }
}
