package org.dreamhorizon.pulseserver.service;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * DUMMY Email Service - Logs emails to console instead of sending them.
 * 
 * This is for development/testing purposes. In production, this should be
 * replaced with actual email sending logic (e.g., SendGrid, AWS SES, etc.).
 */
@Slf4j
@Singleton
public class EmailService {
    
    /**
     * Sends welcome email to user added to tenant
     * 
     * @param email User's email address
     * @param tenantName Name of the tenant
     * @param role Role assigned to user
     * @param adminName Name of admin who added the user
     */
    public void sendTenantWelcomeEmail(String email, String tenantName, String role, String adminName) {
        log.info("📧 ========== EMAIL ==========");
        log.info("📧 To: {}", email);
        log.info("📧 Subject: Welcome to {} on Pulse", tenantName);
        log.info("📧 Body:");
        log.info("📧   Hi there!");
        log.info("📧   ");
        log.info("📧   {} has added you to the \"{}\" organization on Pulse.", adminName, tenantName);
        log.info("📧   Your role: {}", role);
        log.info("📧   ");
        log.info("📧   To get started, simply log in to Pulse:");
        log.info("📧   https://pulse.example.com/login");
        log.info("📧   ");
        log.info("📧   You'll have immediate access to all assigned projects!");
        log.info("📧   ");
        log.info("📧   Best regards,");
        log.info("📧   The Pulse Team");
        log.info("📧 ============================");
    }
    
    /**
     * Sends project access notification email
     * 
     * @param email User's email address
     * @param projectName Name of the project
     * @param role Role assigned to user
     * @param adminName Name of admin who added the user
     * @param projectId Project ID for direct link
     */
    public void sendProjectAccessEmail(String email, String projectName, String role, String adminName, String projectId) {
        log.info("📧 ========== EMAIL ==========");
        log.info("📧 To: {}", email);
        log.info("📧 Subject: You've been added to \"{}\" project", projectName);
        log.info("📧 Body:");
        log.info("📧   Hi there!");
        log.info("📧   ");
        log.info("📧   {} has granted you access to the \"{}\" project on Pulse.", adminName, projectName);
        log.info("📧   Your role: {}", role);
        log.info("📧   ");
        log.info("📧   Log in to start using the project:");
        log.info("📧   https://pulse.example.com/projects/{}", projectId);
        log.info("📧   ");
        log.info("📧   Best regards,");
        log.info("📧   The Pulse Team");
        log.info("📧 ============================");
    }
    
    /**
     * Sends access removed notification email
     * 
     * @param email User's email address
     * @param resourceName Name of tenant or project
     * @param adminName Name of admin who removed the user
     */
    public void sendAccessRemovedEmail(String email, String resourceName, String adminName) {
        log.info("📧 ========== EMAIL ==========");
        log.info("📧 To: {}", email);
        log.info("📧 Subject: Access removed from \"{}\"", resourceName);
        log.info("📧 Body:");
        log.info("📧   Hi there!");
        log.info("📧   ");
        log.info("📧   {} has removed your access to \"{}\" on Pulse.", adminName, resourceName);
        log.info("📧   ");
        log.info("📧   If you believe this was a mistake, please contact your administrator.");
        log.info("📧   ");
        log.info("📧   Best regards,");
        log.info("📧   The Pulse Team");
        log.info("📧 ============================");
    }
    
    /**
     * Sends role update notification email
     * 
     * @param email User's email address
     * @param resourceName Name of tenant or project
     * @param newRole New role assigned
     * @param adminName Name of admin who updated the role
     */
    public void sendRoleUpdatedEmail(String email, String resourceName, String newRole, String adminName) {
        log.info("📧 ========== EMAIL ==========");
        log.info("📧 To: {}", email);
        log.info("📧 Subject: Your role in \"{}\" has been updated", resourceName);
        log.info("📧 Body:");
        log.info("📧   Hi there!");
        log.info("📧   ");
        log.info("📧   {} has updated your role in \"{}\" on Pulse.", adminName, resourceName);
        log.info("📧   Your new role: {}", newRole);
        log.info("📧   ");
        log.info("📧   Log in to see your updated permissions:");
        log.info("📧   https://pulse.example.com/login");
        log.info("📧   ");
        log.info("📧   Best regards,");
        log.info("📧   The Pulse Team");
        log.info("📧 ============================");
    }

    /**
     * Sends project creation notification email with API key
     * 
     * @param email User's email address
     * @param projectName Name of the project
     * @param projectId Project ID
     * @param apiKey Raw API key (only visible in this email)
     */
    public void sendProjectCreatedEmail(String email, String projectName, String projectId, String apiKey) {
        log.info("📧 ========== EMAIL ==========");
        log.info("📧 To: {}", email);
        log.info("📧 Subject: Your project \"{}\" has been created", projectName);
        log.info("📧 Body:");
        log.info("📧   Hi there!");
        log.info("📧   ");
        log.info("📧   Your project \"{}\" has been successfully created on Pulse.", projectName);
        log.info("📧   ");
        log.info("📧   Project ID: {}", projectId);
        log.info("📧   ");
        log.info("📧   Your API Key (save this - it won't be shown again):");
        log.info("📧   {}", apiKey);
        log.info("📧   ");
        log.info("📧   Use this API key to integrate Pulse SDK into your application.");
        log.info("📧   ");
        log.info("📧   Get started:");
        log.info("📧   https://pulse.example.com/projects/{}", projectId);
        log.info("📧   ");
        log.info("📧   Documentation:");
        log.info("📧   https://docs.pulse.example.com/getting-started");
        log.info("📧   ");
        log.info("📧   Best regards,");
        log.info("📧   The Pulse Team");
        log.info("📧 ============================");
    }
}
