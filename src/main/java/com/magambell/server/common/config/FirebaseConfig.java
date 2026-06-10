package com.magambell.server.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.magambell.server.common.enums.ErrorCode;
import com.magambell.server.common.exception.InternalServerException;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.config.json}")
    private String firebaseConfigJson;

    @PostConstruct
    public void init() {
        try {
            log.info("Loading Firebase config");

            if (firebaseConfigJson == null || firebaseConfigJson.isBlank() || firebaseConfigJson.equals("test")) {
                log.warn("Firebase config JSON is missing or invalid. Skipping Firebase initialization.");
                return;
            }
            byte[] decoded = Base64.getDecoder().decode(firebaseConfigJson);
            InputStream serviceAccount = new ByteArrayInputStream(decoded);
            
            // Parse project_id from JSON
            String jsonString = new String(decoded, StandardCharsets.UTF_8);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            String projectId = jsonNode.get("project_id").asText();
            
            // Reset stream for credentials
            serviceAccount = new ByteArrayInputStream(decoded);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setProjectId(projectId)
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp firebaseApp = FirebaseApp.initializeApp(options);
                log.info("Successfully initialized Firebase with project: {}", firebaseApp.getOptions().getProjectId());
            } else {
                log.info("Firebase already initialized with project: {}", FirebaseApp.getInstance().getOptions().getProjectId());
            }

        } catch (Exception e) {
            log.error("Firebase initialization failed: {}", e.getMessage(), e);
            throw new InternalServerException(ErrorCode.FIREBASE_INIT_FAILED);
        }
    }

}
