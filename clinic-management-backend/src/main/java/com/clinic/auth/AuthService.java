package com.clinic.auth;

import com.clinic.clinic.ClinicMember;
import com.clinic.clinic.ClinicMemberRepository;
import com.clinic.clinic.MembershipRole;
import com.clinic.patient.PatientProfile;
import com.clinic.patient.PatientProfileRepository;
import com.clinic.security.FirebasePrincipal;
import com.clinic.user.ClinicUser;
import com.clinic.user.ClinicUserRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AuthService {
    private final ClinicUserRepository users;
    private final PatientProfileRepository patientProfiles;
    private final ClinicMemberRepository clinicMembers;

    public AuthService(ClinicUserRepository users, PatientProfileRepository patientProfiles, ClinicMemberRepository clinicMembers) {
        this.users = users;
        this.patientProfiles = patientProfiles;
        this.clinicMembers = clinicMembers;
    }

    @Transactional
    public UserSession register(FirebasePrincipal principal, RegisterRequest request) {
        if (principal.email() == null || principal.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A Firebase email account is required");
        }
        String fullName = chooseName(request.fullName(), principal.name(), principal.email());
        ClinicUser user = users.findByFirebaseUid(principal.uid()).orElse(null);
        boolean newPatient = user == null;
        if (newPatient) {
            user = users.save(new ClinicUser(principal.uid(), principal.email().toLowerCase(Locale.ROOT), fullName, principal.emailVerified()));
        }
        if (user.isDisabled()) throw new AccessDeniedException("This account is disabled");

        if (request.fullName() != null && !request.fullName().isBlank()) user.setFullName(fullName);
        user.setEmail(principal.email().toLowerCase(Locale.ROOT));
        user.setEmailVerified(principal.emailVerified());
        users.save(user);

        if (newPatient && !patientProfiles.existsByUserId(user.getId())) patientProfiles.save(new PatientProfile(user.getId()));
        return sessionFor(user);
    }

    @Transactional(readOnly = true)
    public UserSession currentUser(FirebasePrincipal principal) {
        ClinicUser user = users.findByFirebaseUid(principal.uid())
                .orElseThrow(() -> new AccessDeniedException("No active clinic account is linked to this sign-in"));
        if (user.isDisabled()) throw new AccessDeniedException("This account is disabled");
        return sessionFor(user);
    }

    private UserSession sessionFor(ClinicUser user) {
        List<ClinicMember> memberships = clinicMembers.findAllByUserIdAndActiveTrue(user.getId());
        var roles = new LinkedHashSet<String>();
        if (patientProfiles.existsByUserId(user.getId())) roles.add(MembershipRole.PATIENT.name());
        List<MembershipSummary> summaries = new ArrayList<>();
        for (ClinicMember membership : memberships) {
            roles.add(membership.getRole().name());
            summaries.add(new MembershipSummary(
                    membership.getId(), membership.getClinicId(), membership.getClinicName(), membership.getRole()));
        }
        return new UserSession(user.getId(), user.getFullName(), user.getEmail(), List.copyOf(roles), List.copyOf(summaries));
    }

    private String chooseName(String supplied, String firebaseName, String email) {
        String name = supplied == null || supplied.isBlank() ? firebaseName : supplied;
        if (name == null || name.isBlank()) name = email.substring(0, email.indexOf('@'));
        name = name.trim();
        if (name.length() < 2 || name.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid full name is required");
        }
        return name;
    }
}
