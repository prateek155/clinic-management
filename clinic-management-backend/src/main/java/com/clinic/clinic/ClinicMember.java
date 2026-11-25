package com.clinic.clinic;

import com.clinic.user.ClinicUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinic_members")
public class ClinicMember {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private ClinicUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MembershipRole role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ClinicMember() {}

    public ClinicMember(Clinic clinic, ClinicUser user, MembershipRole role) {
        this.clinic = clinic;
        this.user = user;
        this.role = role;
    }

    public UUID getId() { return id; }
    public Clinic getClinic() { return clinic; }
    public UUID getClinicId() { return clinic.getId(); }
    public String getClinicName() { return clinic.getName(); }
    public MembershipRole getRole() { return role; }
    public boolean isActive() { return active; }
}
