package org.dreamhorizon.pulseserver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

  EmailService emailService;

  @BeforeEach
  void setUp() {
    emailService = new EmailService();
  }

  @Nested
  class TestSendTenantWelcomeEmail {

    @Test
    void shouldExecuteWithoutThrowing() {
      emailService.sendTenantWelcomeEmail(
          "user@example.com",
          "Acme Corp",
          "admin",
          "Admin User"
      );
    }
  }

  @Nested
  class TestSendProjectAccessEmail {

    @Test
    void shouldExecuteWithoutThrowing() {
      emailService.sendProjectAccessEmail(
          "user@example.com",
          "My Project",
          "viewer",
          "Admin User",
          "proj-123"
      );
    }
  }

  @Nested
  class TestSendAccessRemovedEmail {

    @Test
    void shouldExecuteWithoutThrowing() {
      emailService.sendAccessRemovedEmail(
          "user@example.com",
          "Acme Corp",
          "Admin User"
      );
    }
  }

  @Nested
  class TestSendRoleUpdatedEmail {

    @Test
    void shouldExecuteWithoutThrowing() {
      emailService.sendRoleUpdatedEmail(
          "user@example.com",
          "Acme Corp",
          "editor",
          "Admin User"
      );
    }
  }

  @Nested
  class TestSendProjectCreatedEmail {

    @Test
    void shouldExecuteWithoutThrowing() {
      emailService.sendProjectCreatedEmail(
          "user@example.com",
          "New Project",
          "proj-456",
          "api-key-secret-xyz"
      );
    }
  }
}
