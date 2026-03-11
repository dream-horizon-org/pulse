package org.dreamhorizon.pulseserver.util;

import com.nimbusds.jwt.SignedJWT;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;

/**
 * Utility methods for JWT authentication and authorization.
 * Provides centralized token extraction and claim parsing to avoid code duplication.
 */
@Slf4j
@UtilityClass
public class AuthenticationUtil {
    
    private static final String BEARER_PREFIX = "Bearer ";
    
    /**
     * Extract JWT token from Authorization header.
     * 
     * @param authorization Authorization header value (e.g., "Bearer eyJhbGc...")
     * @return JWT token without "Bearer " prefix
     * @throws IllegalArgumentException if header is missing or malformed
     */
    public static String extractToken(String authorization) {
        if (authorization == null || authorization.isEmpty()) {
            throw new IllegalArgumentException("Authorization header is required");
        }
        
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException(
                "Authorization header must start with 'Bearer '");
        }
        
        return authorization.substring(BEARER_PREFIX.length());
    }
    
    /**
     * Extract user ID from JWT token in Authorization header.
     * 
     * @param authorization Authorization header value
     * @return User ID from JWT claims
     * @throws IllegalArgumentException if header is invalid or userId claim is missing
     * @throws RuntimeException if token cannot be parsed
     */
    public static String extractUserId(String authorization) {
        String token = extractToken(authorization);
        
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            String userId = jwt.getJWTClaimsSet().getSubject();
            
            if (userId == null || userId.isEmpty()) {
                throw new IllegalArgumentException("Token missing subject (userId) claim");
            }
            
            return userId;
            
        } catch (ParseException e) {
            log.error("Failed to parse JWT token", e);
            throw new RuntimeException("Invalid JWT token", e);
        }
    }
    
    /**
     * Extract tenant ID from JWT token in Authorization header.
     * 
     * @param authorization Authorization header value
     * @return Tenant ID from JWT claims, or null if not present
     * @throws RuntimeException if token cannot be parsed
     */
    public static String extractTenantId(String authorization) {
        String token = extractToken(authorization);
        
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            return jwt.getJWTClaimsSet().getStringClaim("tenantId");
        } catch (ParseException e) {
            log.error("Failed to parse JWT token", e);
            throw new RuntimeException("Invalid JWT token", e);
        }
    }
}
