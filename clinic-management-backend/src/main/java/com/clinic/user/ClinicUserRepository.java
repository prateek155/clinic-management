package com.clinic.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicUserRepository extends JpaRepository<ClinicUser, UUID> {
    Optional<ClinicUser> findByFirebaseUid(String firebaseUid);
}
