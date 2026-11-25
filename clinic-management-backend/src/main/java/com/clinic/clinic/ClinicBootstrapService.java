package com.clinic.clinic;

import com.clinic.user.ClinicUser;
import com.clinic.user.ClinicUserRepository;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicBootstrapService {
    private final ClinicUserRepository users;
    private final ClinicRepository clinics;
    private final ClinicMemberRepository members;
    private final DoctorProfileRepository doctorProfiles;

    public ClinicBootstrapService(ClinicUserRepository users, ClinicRepository clinics,
            ClinicMemberRepository members, DoctorProfileRepository doctorProfiles) {
        this.users = users;
        this.clinics = clinics;
        this.members = members;
        this.doctorProfiles = doctorProfiles;
    }

    @Transactional
    public void ensureOwner(String uid, String email, String name, boolean emailVerified, String clinicName) {
        String normalizedEmail = email.toLowerCase(java.util.Locale.ROOT);
        String ownerName = name == null || name.isBlank() ? normalizedEmail.substring(0, normalizedEmail.indexOf('@')) : name;
        ClinicUser user = users.findByFirebaseUid(uid).orElseGet(() ->
                users.save(new ClinicUser(uid, normalizedEmail, ownerName, emailVerified)));
        if (user.isDisabled()) throw new AccessDeniedException("The configured clinic owner account is disabled");
        if (members.existsByUserIdAndRoleAndActiveTrue(user.getId(), MembershipRole.DOCTOR)) return;

        String slug = clinicName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.isBlank()) slug = "clinic";
        slug = slug + "-" + UUID.randomUUID().toString().substring(0, 8);
        Clinic clinic = clinics.save(new Clinic(clinicName, slug));
        ClinicMember member = members.save(new ClinicMember(clinic, user, MembershipRole.DOCTOR));
        doctorProfiles.save(new DoctorProfile(clinic.getId(), member.getId()));
    }
}
