package org.dreamhorizon.pulseserver.model;

import lombok.Builder;
import lombok.Data;

/**
 * Model representing ClickHouse credentials for a project.
 * Each project has its own dedicated ClickHouse user for data isolation.
 */
@Data
@Builder
public class ClickhouseProjectCredentials {
    private Long id;
    private String projectId;                      // Project ID (proj-{uuid})
    private String clickhouseUsername;             // ClickHouse username (project_{id})
    private String clickhousePasswordEncrypted;    // AES encrypted password
    private String encryptionSalt;                 // Salt for encryption
    private String passwordDigest;                 // SHA-256 digest for verification
    private Boolean isActive;                      // Credential status
    private String createdAt;
    private String updatedAt;
}
