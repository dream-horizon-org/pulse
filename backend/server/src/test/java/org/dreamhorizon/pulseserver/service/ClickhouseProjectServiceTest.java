package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
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
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

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

  @Nested
  class CreateClickhouseUserAndPolicies {

    @Test
    void shouldCallPoolManagerForAdminPoolAndClusterClause() {
      ConnectionPool mockPool = mock(ConnectionPool.class);
      Connection mockConnection = mock(Connection.class);
      Statement mockStatement = mock(Statement.class);
      Result mockResult = mock(Result.class);

      when(poolManager.getAdminPool()).thenReturn(mockPool);
      when(poolManager.getOnClusterClause()).thenReturn("");
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));
      when(mockConnection.createStatement(anyString())).thenReturn(mockStatement);
      when(mockStatement.execute()).thenReturn(Mono.just(mockResult));
      when(mockConnection.close()).thenReturn(Mono.empty());

      Completable result = service.createClickhouseUserAndPolicies("proj-123", "project_123", "password");
      result.test().assertComplete();

      verify(poolManager).getAdminPool();
      verify(poolManager).getOnClusterClause();
    }

    @Test
    void shouldIncludeOnClusterClauseWhenConfigured() {
      ConnectionPool mockPool = mock(ConnectionPool.class);
      Connection mockConnection = mock(Connection.class);
      Statement mockStatement = mock(Statement.class);
      Result mockResult = mock(Result.class);

      when(poolManager.getAdminPool()).thenReturn(mockPool);
      when(poolManager.getOnClusterClause()).thenReturn(" ON CLUSTER 'pulse-clickhouse'");
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));
      when(mockConnection.createStatement(anyString())).thenReturn(mockStatement);
      when(mockStatement.execute()).thenReturn(Mono.just(mockResult));
      when(mockConnection.close()).thenReturn(Mono.empty());

      Completable result = service.createClickhouseUserAndPolicies("proj-123", "project_123", "password");
      result.test().assertComplete();

      verify(poolManager).getOnClusterClause();
    }

    @Test
    void shouldCreateUserAndPoliciesForAllTables() {
      ConnectionPool mockPool = mock(ConnectionPool.class);
      Connection mockConnection = mock(Connection.class);
      Statement mockStatement = mock(Statement.class);
      Result mockResult = mock(Result.class);

      when(poolManager.getAdminPool()).thenReturn(mockPool);
      when(poolManager.getOnClusterClause()).thenReturn("");
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));
      when(mockConnection.createStatement(anyString())).thenReturn(mockStatement);
      when(mockStatement.execute()).thenReturn(Mono.just(mockResult));
      when(mockConnection.close()).thenReturn(Mono.empty());

      service.createClickhouseUserAndPolicies("proj-123", "project_123", "password")
          .test()
          .assertComplete();

      // 1 CREATE USER + 4 CREATE ROW POLICY + 1 GRANT = 6 SQL statements
      verify(mockConnection, times(6)).createStatement(anyString());
    }
  }

  @Nested
  class RemoveProjectClickhouseUser {

    @Test
    void shouldDropPoliciesAndUserWithClusterClause() {
      ConnectionPool mockPool = mock(ConnectionPool.class);
      Connection mockConnection = mock(Connection.class);
      Statement mockStatement = mock(Statement.class);
      Result mockResult = mock(Result.class);

      when(poolManager.getAdminPool()).thenReturn(mockPool);
      when(poolManager.getOnClusterClause()).thenReturn(" ON CLUSTER 'pulse-clickhouse'");
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));
      when(mockConnection.createStatement(anyString())).thenReturn(mockStatement);
      when(mockStatement.execute()).thenReturn(Mono.just(mockResult));
      when(mockConnection.close()).thenReturn(Mono.empty());
      when(credentialsDao.deactivateCredentials("proj-123")).thenReturn(Completable.complete());
      doNothing().when(poolManager).closePoolForProject("proj-123");
      when(credentialsDao.insertAuditLog(eq("proj-123"), eq(ProjectAuditAction.CREDENTIALS_REMOVED), 
          eq("admin"), any(JsonObject.class))).thenReturn(Completable.complete());

      service.removeProjectClickhouseUser("proj-123", "admin")
          .test()
          .assertComplete();

      verify(poolManager).getOnClusterClause();
      verify(credentialsDao).deactivateCredentials("proj-123");
      verify(poolManager).closePoolForProject("proj-123");
    }

    @Test
    void shouldDropAllPoliciesAndUser() {
      ConnectionPool mockPool = mock(ConnectionPool.class);
      Connection mockConnection = mock(Connection.class);
      Statement mockStatement = mock(Statement.class);
      Result mockResult = mock(Result.class);

      when(poolManager.getAdminPool()).thenReturn(mockPool);
      when(poolManager.getOnClusterClause()).thenReturn("");
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));
      when(mockConnection.createStatement(anyString())).thenReturn(mockStatement);
      when(mockStatement.execute()).thenReturn(Mono.just(mockResult));
      when(mockConnection.close()).thenReturn(Mono.empty());
      when(credentialsDao.deactivateCredentials("proj-456")).thenReturn(Completable.complete());
      doNothing().when(poolManager).closePoolForProject("proj-456");
      when(credentialsDao.insertAuditLog(eq("proj-456"), eq(ProjectAuditAction.CREDENTIALS_REMOVED), 
          eq("user1"), any(JsonObject.class))).thenReturn(Completable.complete());

      service.removeProjectClickhouseUser("proj-456", "user1")
          .test()
          .assertComplete();

      // 4 DROP ROW POLICY + 1 DROP USER = 5 SQL statements
      verify(mockConnection, times(5)).createStatement(anyString());
    }
  }

  @Nested
  class RotateProjectClickhousePassword {

    @Test
    void shouldAlterUserPasswordWithClusterClause() {
      ConnectionPool mockPool = mock(ConnectionPool.class);
      Connection mockConnection = mock(Connection.class);
      Statement mockStatement = mock(Statement.class);
      Result mockResult = mock(Result.class);
      ClickhouseProjectCredentials savedCreds = ClickhouseProjectCredentials.builder()
          .projectId("proj-789")
          .clickhouseUsername("project_789")
          .build();

      when(poolManager.getAdminPool()).thenReturn(mockPool);
      when(poolManager.getOnClusterClause()).thenReturn(" ON CLUSTER 'test-cluster'");
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));
      when(mockConnection.createStatement(anyString())).thenReturn(mockStatement);
      when(mockStatement.execute()).thenReturn(Mono.just(mockResult));
      when(mockConnection.close()).thenReturn(Mono.empty());
      when(credentialsDao.saveCredentials(eq("proj-789"), anyString(), anyString()))
          .thenReturn(Single.just(savedCreds));
      doNothing().when(poolManager).closePoolForProject("proj-789");
      when(credentialsDao.insertAuditLog(eq("proj-789"), eq(ProjectAuditAction.CREDENTIALS_ROTATED), 
          eq("admin"), any(JsonObject.class))).thenReturn(Completable.complete());

      service.rotateProjectClickhousePassword("proj-789", "admin")
          .test()
          .assertComplete();

      verify(poolManager).getOnClusterClause();
      verify(poolManager).closePoolForProject("proj-789");
    }

    @Test
    void shouldSaveNewCredentialsAndClosePool() {
      ConnectionPool mockPool = mock(ConnectionPool.class);
      Connection mockConnection = mock(Connection.class);
      Statement mockStatement = mock(Statement.class);
      Result mockResult = mock(Result.class);
      ClickhouseProjectCredentials savedCreds = ClickhouseProjectCredentials.builder()
          .projectId("proj-rotate")
          .clickhouseUsername("project_rotate")
          .build();

      when(poolManager.getAdminPool()).thenReturn(mockPool);
      when(poolManager.getOnClusterClause()).thenReturn("");
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));
      when(mockConnection.createStatement(anyString())).thenReturn(mockStatement);
      when(mockStatement.execute()).thenReturn(Mono.just(mockResult));
      when(mockConnection.close()).thenReturn(Mono.empty());
      when(credentialsDao.saveCredentials(eq("proj-rotate"), anyString(), anyString()))
          .thenReturn(Single.just(savedCreds));
      doNothing().when(poolManager).closePoolForProject("proj-rotate");
      when(credentialsDao.insertAuditLog(eq("proj-rotate"), eq(ProjectAuditAction.CREDENTIALS_ROTATED), 
          eq("rotator"), any(JsonObject.class))).thenReturn(Completable.complete());

      service.rotateProjectClickhousePassword("proj-rotate", "rotator")
          .test()
          .assertComplete();

      verify(credentialsDao).saveCredentials(eq("proj-rotate"), anyString(), anyString());
      verify(poolManager).closePoolForProject("proj-rotate");
      verify(credentialsDao).insertAuditLog(eq("proj-rotate"), eq(ProjectAuditAction.CREDENTIALS_ROTATED), 
          eq("rotator"), any(JsonObject.class));
    }
  }

  @Nested
  class SetupProjectClickhouseUser {

    @Test
    void shouldCreateUserAndSaveCredentialsWithAuditLog() {
      ConnectionPool mockPool = mock(ConnectionPool.class);
      Connection mockConnection = mock(Connection.class);
      Statement mockStatement = mock(Statement.class);
      Result mockResult = mock(Result.class);
      ClickhouseProjectCredentials savedCreds = ClickhouseProjectCredentials.builder()
          .projectId("proj-setup")
          .clickhouseUsername("project_setup")
          .build();

      when(poolManager.getAdminPool()).thenReturn(mockPool);
      when(poolManager.getOnClusterClause()).thenReturn("");
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));
      when(mockConnection.createStatement(anyString())).thenReturn(mockStatement);
      when(mockStatement.execute()).thenReturn(Mono.just(mockResult));
      when(mockConnection.close()).thenReturn(Mono.empty());
      when(credentialsDao.saveCredentials(eq("proj-setup"), anyString(), anyString()))
          .thenReturn(Single.just(savedCreds));
      when(credentialsDao.insertAuditLog(eq("proj-setup"), eq(ProjectAuditAction.CREDENTIALS_SETUP), 
          eq("setupUser"), any(JsonObject.class))).thenReturn(Completable.complete());

      service.setupProjectClickhouseUser("proj-setup", "setupUser")
          .test()
          .assertComplete();

      verify(credentialsDao).saveCredentials(eq("proj-setup"), anyString(), anyString());
      verify(credentialsDao).insertAuditLog(eq("proj-setup"), eq(ProjectAuditAction.CREDENTIALS_SETUP), 
          eq("setupUser"), any(JsonObject.class));
    }
  }
}
