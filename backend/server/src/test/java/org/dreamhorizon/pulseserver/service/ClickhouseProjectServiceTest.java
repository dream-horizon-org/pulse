package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseProjectConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsDao;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.models.ClickhouseProjectCredentialAudit;
import org.dreamhorizon.pulseserver.model.ClickhouseProjectCredentials;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import io.vertx.rxjava3.sqlclient.SqlConnection;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

  @Nested
  class SaveCredentials {

    @Test
    void shouldPropagateErrorWhenDaoFails() {
      SqlConnection conn = org.mockito.Mockito.mock(SqlConnection.class);
      when(credentialsDao.saveCredentials(eq(conn), eq("proj_456"), anyString(), anyString()))
          .thenReturn(Single.error(new RuntimeException("DB connection failed")));

      Single<ClickhouseProjectService.CredentialsResult> result = service.saveCredentials(conn, "proj_456");
      result.test().assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("DB connection failed"));
    }

    @Test
    void shouldReturnCredentialsResultWhenDaoSucceeds() {
      SqlConnection conn = org.mockito.Mockito.mock(SqlConnection.class);
      ClickhouseProjectCredentials savedCreds = ClickhouseProjectCredentials.builder()
          .projectId("proj_123")
          .clickhouseUsername("project_123")
          .clickhousePasswordEncrypted("enc")
          .encryptionSalt("salt")
          .passwordDigest("digest")
          .isActive(true)
          .build();
      when(credentialsDao.saveCredentials(eq(conn), eq("proj_123"), anyString(), anyString()))
          .thenReturn(Single.just(savedCreds));

      Single<ClickhouseProjectService.CredentialsResult> result = service.saveCredentials(conn, "proj_123");
      ClickhouseProjectService.CredentialsResult credsResult = result.blockingGet();

      assertThat(credsResult.getProjectId()).isEqualTo("proj_123");
      assertThat(credsResult.getUsername()).isEqualTo("project_123");
      assertThat(credsResult.getPlainPassword()).isNotNull();
      assertThat(credsResult.getPlainPassword()).isNotEmpty();
    }
  }

  @Nested
  class GetAuditHistory {

    @Test
    void shouldPropagateErrorWhenDaoFails() {
      when(credentialsDao.getAuditLogsByProjectId("proj_err"))
          .thenReturn(Flowable.error(new RuntimeException("Audit fetch failed")));

      service.getAuditHistory("proj_err")
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("Audit fetch failed"));
    }

    @Test
    void shouldReturnAuditLogsFromDao() {
      ClickhouseProjectCredentialAudit audit = ClickhouseProjectCredentialAudit.builder()
          .id(1L)
          .projectId("proj_123")
          .action("CREDENTIALS_SETUP")
          .performedBy("user1")
          .details("{}")
          .createdAt("2024-01-01T00:00:00")
          .build();
      when(credentialsDao.getAuditLogsByProjectId("proj_123"))
          .thenReturn(Flowable.just(audit));

      List<ClickhouseProjectCredentialAudit> result = service.getAuditHistory("proj_123")
          .toList()
          .blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getProjectId()).isEqualTo("proj_123");
      assertThat(result.get(0).getAction()).isEqualTo("CREDENTIALS_SETUP");
    }
  }

  @Nested
  class GetRecentAuditLogs {

    @Test
    void shouldPropagateErrorWhenDaoFails() {
      when(credentialsDao.getRecentAuditLogs(5))
          .thenReturn(Flowable.error(new RuntimeException("Recent logs failed")));

      service.getRecentAuditLogs(5)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("Recent logs failed"));
    }

    @Test
    void shouldReturnRecentLogsFromDao() {
      ClickhouseProjectCredentialAudit audit = ClickhouseProjectCredentialAudit.builder()
          .id(1L)
          .projectId("proj_123")
          .action("CREDENTIALS_ROTATED")
          .performedBy("admin")
          .details("{}")
          .createdAt("2024-01-01T00:00:00")
          .build();
      when(credentialsDao.getRecentAuditLogs(10))
          .thenReturn(Flowable.just(audit));

      List<ClickhouseProjectCredentialAudit> result = service.getRecentAuditLogs(10)
          .toList()
          .blockingGet();

      assertThat(result).hasSize(1);
      verify(credentialsDao).getRecentAuditLogs(10);
    }
  }

  @Nested
  class GetCredentialsByProjectId {

    @Test
    void shouldReturnCredentialsWhenFound() {
      ClickhouseProjectCredentials creds = ClickhouseProjectCredentials.builder()
          .projectId("proj_123")
          .clickhouseUsername("project_123")
          .clickhousePasswordEncrypted("decrypted")
          .encryptionSalt("salt")
          .passwordDigest("digest")
          .isActive(true)
          .build();
      when(credentialsDao.getCredentialsByProjectId("proj_123"))
          .thenReturn(Maybe.just(creds));

      Maybe<ClickhouseProjectCredentials> result = service.getCredentialsByProjectId("proj_123");
      assertThat(result.blockingGet()).isEqualTo(creds);
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
      when(credentialsDao.getCredentialsByProjectId("proj_999"))
          .thenReturn(Maybe.empty());

      Maybe<ClickhouseProjectCredentials> result = service.getCredentialsByProjectId("proj_999");
      result.test().assertNoValues().assertComplete();
    }

    @Test
    void shouldPropagateErrorWhenDaoFails() {
      when(credentialsDao.getCredentialsByProjectId("proj_err"))
          .thenReturn(Maybe.error(new RuntimeException("DB error")));

      service.getCredentialsByProjectId("proj_err")
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("DB error"));
    }
  }
}
