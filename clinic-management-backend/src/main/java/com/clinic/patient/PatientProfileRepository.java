package com.clinic.patient;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientProfileRepository extends JpaRepository<PatientProfile, UUID> {
    boolean existsByUserId(UUID userId);
}
