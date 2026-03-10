package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseProjectConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsDao;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickhouseProjectServiceTest {

  @Mock
  ClickhouseProjectConnectionPoolManager poolManager;

  @Mock
  ClickhouseProjectCredentialsDao credentialsDao;

  @InjectMocks
  ClickhouseProjectService service;

  @Nested
  class GenerateUsername {

    @Test
    void shouldReplaceDashesWithUnderscores() {
      String result = service.generateUsername("proj-abc-def");
      assertThat(result).isEqualTo("project_abc_def");
    }

    @Test
    void shouldRemoveProjPrefix() {
      String result = service.generateUsername("proj_xyz");
      assertThat(result).isEqualTo("project_xyz");
    }

    @Test
    void shouldHandleBothDashesAndProjPrefix() {
      String result = service.generateUsername("proj_abc-def-123");
      assertThat(result).isEqualTo("project_abc_def_123");
    }

    @Test
    void shouldAddProjectPrefix() {
      String result = service.generateUsername("my-id");
      assertThat(result).isEqualTo("project_my_id");
    }

    @Test
    void shouldHandlePlainProjectId() {
      String result = service.generateUsername("abc123");
      assertThat(result).isEqualTo("project_abc123");
    }
  }

  @Nested
  class GeneratePassword {

    @Test
    void shouldNotReturnNull() {
      String result = service.generatePassword();
      assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnNonEmptyString() {
      String result = service.generatePassword();
      assertThat(result).hasSizeGreaterThan(0);
    }

    @Test
    void shouldReturnUniquePasswordsAcrossCalls() {
      Set<String> passwords = new HashSet<>();
      for (int i = 0; i < 50; i++) {
        String password = service.generatePassword();
        assertThat(passwords).doesNotContain(password);
        passwords.add(password);
      }
    }

    @Test
    void shouldReturnBase64UrlSafeWithoutPadding() {
      String result = service.generatePassword();
      assertThat(result).doesNotContain("+", "/", "=");
    }
  }

  @Nested
  class CredentialsResult {

    @Test
    void shouldReturnProjectIdFromGetter() {
      var result = new ClickhouseProjectService.CredentialsResult("proj_123", "project_123", "secret");
      assertThat(result.getProjectId()).isEqualTo("proj_123");
    }

    @Test
    void shouldReturnUsernameFromGetter() {
      var result = new ClickhouseProjectService.CredentialsResult("proj_123", "project_123", "secret");
      assertThat(result.getUsername()).isEqualTo("project_123");
    }

    @Test
    void shouldReturnPlainPasswordFromGetter() {
      var result = new ClickhouseProjectService.CredentialsResult("proj_123", "project_123", "secret");
      assertThat(result.getPlainPassword()).isEqualTo("secret");
    }
  }
}
