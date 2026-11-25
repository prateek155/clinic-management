package com.clinic.clinic;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, UUID> {}
