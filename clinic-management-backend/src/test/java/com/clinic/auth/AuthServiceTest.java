package com.clinic.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.clinic.clinic.ClinicMemberRepository;
import com.clinic.patient.PatientProfileRepository;
import com.clinic.security.FirebasePrincipal;
import com.clinic.user.ClinicUser;
import com.clinic.user.ClinicUserRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock ClinicUserRepository users;
    @Mock PatientProfileRepository patientProfiles;
    @Mock ClinicMemberRepository clinicMembers;

    private AuthService authService;
    private ClinicUser user;

    @BeforeEach
    void setUp() throws Exception {
        authService = new AuthService(users, patientProfiles, clinicMembers);
        user = new ClinicUser("firebase-uid", "patient@example.test", "Asha Rao", true);
        Field id = ClinicUser.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, UUID.randomUUID());
    }

    @Test
    void currentSessionGetsRolesFromApplicationProfiles() {
        when(users.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(user));
        when(patientProfiles.existsByUserId(user.getId())).thenReturn(true);
        when(clinicMembers.findAllByUserIdAndActiveTrue(user.getId())).thenReturn(List.of());

        UserSession session = authService.currentUser(new FirebasePrincipal("firebase-uid", user.getEmail(), "Asha Rao", true));

        assertEquals(List.of("PATIENT"), session.roles());
        assertEquals(user.getId(), session.id());
        assertEquals("Asha Rao", session.fullName());
    }
}
