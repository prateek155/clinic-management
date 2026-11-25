package com.clinic.clinic;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicMemberRepository extends JpaRepository<ClinicMember, UUID> {
    @EntityGraph(attributePaths = "clinic")
    List<ClinicMember> findAllByUserIdAndActiveTrue(UUID userId);

    boolean existsByUserIdAndRoleAndActiveTrue(UUID userId, MembershipRole role);
}
