package org.dreamhorizon.pulseserver.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for JWT token operations.
 * Provides helper methods for extracting claims from tokens.
 * Uses simple JSON parsing instead of jjwt library.
 */
@Slf4j
public class JwtUtils {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Extract user ID from JWT token.
     * User ID is stored in the "subject" or "sub" claim.
     * 
     * @param token JWT token
     * @return User ID
     */
    public static String extractUserId(String token) {
        try {
            JsonNode claims = parseToken(token);
            JsonNode sub = claims.get("sub");
            if (sub != null && !sub.isNull()) {
                return sub.asText();
            }
            JsonNode subject = claims.get("subject");
            return subject != null ? subject.asText() : null;
        } catch (Exception e) {
            log.error("Failed to extract user ID from token", e);
            throw new IllegalArgumentException("Invalid token: unable to extract user ID", e);
        }
    }
    
    /**
     * Extract tenant ID from JWT token.
     * Tenant ID is stored in the "tenantId" claim.
     * 
     * @param token JWT token
     * @return Tenant ID, or null if not present
     */
    public static String extractTenantId(String token) {
        try {
            JsonNode claims = parseToken(token);
            JsonNode tenantId = claims.get("tenantId");
            return tenantId != null && !tenantId.isNull() ? tenantId.asText() : null;
        } catch (Exception e) {
            log.debug("Failed to extract tenant ID from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract email from JWT token.
     * 
     * @param token JWT token
     * @return Email address
     */
    public static String extractEmail(String token) {
        try {
            JsonNode claims = parseToken(token);
            JsonNode email = claims.get("email");
            return email != null && !email.isNull() ? email.asText() : null;
        } catch (Exception e) {
            log.error("Failed to extract email from token", e);
            return null;
        }
    }
    
    /**
     * Parse JWT token without verification (for claim extraction only).
     * Note: This should only be used after token has been verified by JwtService.
     * 
     * @param token JWT token
     * @return JsonNode with claims
     */
    private static JsonNode parseToken(String token) {
        try {
            // Parse without signature verification (assumes already verified)
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT token format");
            }
            
            // Decode claims (middle part - payload)
            String claimsJson = new String(
                java.util.Base64.getUrlDecoder().decode(parts[1]),
                java.nio.charset.StandardCharsets.UTF_8
            );
            
            // Parse JSON to JsonNode
            return objectMapper.readTree(claimsJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JWT token", e);
        }
    }
    
    /**
     * Check if token is a Firebase token based on issuer.
     * 
     * @param issuer Token issuer claim
     * @return true if Firebase token
     */
    public static boolean isFirebaseIssuer(String issuer) {
        return issuer != null && issuer.contains("securetoken.google.com");
    }
    
    /**
     * Extract issuer from token.
     * 
     * @param token JWT token
     * @return Issuer string
     */
    public static String jwtIssuer(String token) {
        try {
            JsonNode claims = parseToken(token);
            JsonNode iss = claims.get("iss");
            return iss != null && !iss.isNull() ? iss.asText() : null;
        } catch (Exception e) {
            log.error("Failed to extract issuer from token", e);
            return null;
        }
    }
}
