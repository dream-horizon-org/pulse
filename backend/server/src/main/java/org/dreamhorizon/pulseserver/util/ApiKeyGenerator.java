package org.dreamhorizon.pulseserver.util;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility for generating and parsing project API keys.
 * Format: pulse_{projectId}_sk_{randomKey}
 * 
 * The API key embeds the project ID for easy extraction and includes
 * a cryptographically secure random component.
 */
@Slf4j
@Singleton
public class ApiKeyGenerator {
    
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int KEY_LENGTH = 32;
    private static final String PREFIX = "pulse";
    private static final String SEPARATOR = "_";
    private static final String KEY_TYPE = "sk";
    
    /**
     * Generate a new API key for a project.
     * Format: pulse_{projectId}_sk_{randomKey}
     * 
     * @param projectId The project ID
     * @return Generated API key
     */
    public String generate(String projectId) {
        byte[] bytes = new byte[KEY_LENGTH];
        RANDOM.nextBytes(bytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        
        String apiKey = String.format("%s%s%s%s%s%s%s", 
            PREFIX, SEPARATOR, 
            projectId, SEPARATOR, 
            KEY_TYPE, SEPARATOR, 
            randomPart);
            
        log.debug("Generated API key for project: {}", projectId);
        return apiKey;
    }
    
    /**
     * Extract project ID from an API key.
     * 
     * @param apiKey The API key
     * @return The extracted project ID
     * @throws IllegalArgumentException if the API key format is invalid
     */
    public String extractProjectId(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        
        String[] parts = apiKey.split(SEPARATOR);
        
        // Expected format: pulse_{projectId}_sk_{random}
        // parts[0] = "pulse", parts[1] = projectId, parts[2] = "sk", parts[3] = random
        if (parts.length != 4 || !PREFIX.equals(parts[0]) || !KEY_TYPE.equals(parts[2])) {
            throw new IllegalArgumentException("Invalid API key format: " + apiKey);
        }
        
        return parts[1];
    }
    
    /**
     * Validate API key format without database lookup.
     * 
     * @param apiKey The API key to validate
     * @return true if format is valid, false otherwise
     */
    public boolean isValidFormat(String apiKey) {
        try {
            extractProjectId(apiKey);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
