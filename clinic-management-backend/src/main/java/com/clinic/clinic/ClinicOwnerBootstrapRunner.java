package com.clinic.clinic;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.clinic-bootstrap.enabled", havingValue = "true")
public class ClinicOwnerBootstrapRunner implements ApplicationRunner {
    private final ObjectProvider<FirebaseAuth> firebaseAuth;
    private final ClinicBootstrapService bootstrapService;
    private final String ownerEmail;
    private final String clinicName;

    public ClinicOwnerBootstrapRunner(ObjectProvider<FirebaseAuth> firebaseAuth, ClinicBootstrapService bootstrapService,
            @Value("${app.clinic-bootstrap.owner-email}") String ownerEmail,
            @Value("${app.clinic-bootstrap.clinic-name}") String clinicName) {
        this.firebaseAuth = firebaseAuth;
        this.bootstrapService = bootstrapService;
        this.ownerEmail = ownerEmail;
        this.clinicName = clinicName;
    }

    @Override
    public void run(ApplicationArguments args) throws FirebaseAuthException {
        FirebaseAuth admin = firebaseAuth.getIfAvailable();
        if (admin == null) throw new IllegalStateException("Enable Firebase Admin before bootstrapping the clinic owner");
        if (ownerEmail.isBlank()) throw new IllegalStateException("BOOTSTRAP_OWNER_EMAIL is required when clinic bootstrap is enabled");
        UserRecord owner = admin.getUserByEmail(ownerEmail);
        if (owner.isDisabled() || owner.getEmail() == null) throw new IllegalStateException("The configured clinic owner must be an active Firebase email account");
        bootstrapService.ensureOwner(owner.getUid(), owner.getEmail(), owner.getDisplayName(), owner.isEmailVerified(), clinicName);
    }
}
