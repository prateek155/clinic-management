package com.clinic.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true")
public class FirebaseAdminConfiguration {
    @Bean
    FirebaseApp firebaseApp(@Value("${firebase.project-id}") String projectId,
            @Value("${firebase.storage.bucket:}") String storageBucket) throws IOException {
        if (projectId.isBlank()) {
            throw new IllegalStateException("FIREBASE_PROJECT_ID is required when Firebase authentication is enabled");
        }
        return FirebaseApp.getApps().stream()
                .filter(app -> app.getName().equals(FirebaseApp.DEFAULT_APP_NAME))
                .findFirst()
                .orElseGet(() -> initialize(projectId, storageBucket));
    }

    @Bean
    FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    @Bean
    com.google.firebase.cloud.StorageClient firebaseStorageClient(FirebaseApp firebaseApp) {
        return com.google.firebase.cloud.StorageClient.getInstance(firebaseApp);
    }

    private FirebaseApp initialize(String projectId, String storageBucket) {
        try {
            FirebaseOptions.Builder options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(projectId);
            if (storageBucket != null && !storageBucket.isBlank()) options.setStorageBucket(storageBucket.trim());
            return FirebaseApp.initializeApp(options.build());
        } catch (IOException exception) {
            throw new IllegalStateException("Firebase Application Default Credentials are unavailable", exception);
        }
    }
}
