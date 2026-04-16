package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseProjectConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsDao;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.models.ClickhouseProjectCredentialAudit;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ClickhouseProjectService {

  private final ClickhouseProjectConnectionPoolManager poolManager;
  private final ClickhouseProjectCredentialsDao credentialsDao;

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int PASSWORD_LENGTH = 32;

  /** Row policy applies to all tables in this database (see ClickHouse {@code ON db.*}). */
  private static final String OTEL_DB_ALL_TABLES = "otel.*";

  private static final String ROOT_CAUSE_CACHE_TABLE = "otel.root_cause_cache";

  // ==================== CREDENTIAL GENERATION (Pure, no I/O) ====================

  public String generateUsername(String projectId) {
    String sanitized = projectId.replace("-", "_").replace("proj_", "");
    return "project_" + sanitized;
  }

  public String generatePassword() {
    byte[] bytes = new byte[PASSWORD_LENGTH];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  // ==================== TRANSACTIONAL METHODS ====================

  /**
   * Saves ClickHouse credentials to MySQL within a transaction.
   * Used during project creation to include credentials insertion in the main transaction.
   *
   * @param conn      The SQL connection for the transaction
   * @param projectId The project ID
   * @return Single containing the credentials result with plain password for later CH user creation
   */
  public Single<CredentialsResult> saveCredentials(SqlConnection conn, String projectId) {
    String username = generateUsername(projectId);
    String plainPassword = generatePassword();

    log.debug("Saving ClickHouse credentials for project: {} within transaction", projectId);

    return credentialsDao.saveCredentials(conn, projectId, username, plainPassword)
        .map(creds -> new CredentialsResult(projectId, username, plainPassword))
        .doOnSuccess(result -> log.debug("Saved ClickHouse credentials for project: {} within transaction", projectId))
        .doOnError(error -> log.error("Failed to save ClickHouse credentials for project: {} within transaction", projectId, error));
  }

  // ==================== CLICKHOUSE OPERATIONS (No MySQL) ====================

  public Completable createClickhouseUserAndPolicies(String projectId, String username, String password) {
    log.info("Creating ClickHouse user and policies for project: {}, username: {}", projectId, username);

    return Completable.fromAction(() -> {
          ConnectionPool adminPool = poolManager.getAdminPool();
          String onCluster = poolManager.getOnClusterClause();

          // Step 1: Create ClickHouse user
          String createUserSQL = String.format(
              "CREATE USER IF NOT EXISTS %s%s IDENTIFIED WITH sha256_password BY '%s'",
              username, onCluster, password
          );
          executeSQL(adminPool, createUserSQL);
          log.info("Created ClickHouse user: {}{}", username, onCluster.isBlank() ? "" : " on cluster");

          // Step 2: Single DB-wide row policy on otel.* (PERMISSIVE; same ProjectId filter for all tables)
          String policyName = generatePolicyName(projectId);
          String createPolicySQL = String.format(
              "CREATE ROW POLICY IF NOT EXISTS %s%s ON %s AS PERMISSIVE " +
                  "FOR SELECT USING ProjectId = '%s' TO %s",
              policyName, onCluster, OTEL_DB_ALL_TABLES, projectId, username
          );
          executeSQL(adminPool, createPolicySQL);
          log.debug("Created row policy: {} on {}", policyName, OTEL_DB_ALL_TABLES);

          // Step 3: Grant SELECT permissions
          String grantSQL = String.format("GRANT%s SELECT ON otel.* TO %s", onCluster, username);
          executeSQL(adminPool, grantSQL);
          log.info("Granted SELECT permissions to: {}", username);

          // root_cause_cache uses ProjectId like other otel.* tables; DB-wide row policy above applies.

          // Step 4: Grant INSERT on root_cause_cache for cache upsert
          String grantInsertSQL = String.format("GRANT%s INSERT ON %s TO %s", onCluster, ROOT_CAUSE_CACHE_TABLE, username);
          executeSQL(adminPool, grantInsertSQL);
          log.info("Granted INSERT on {} to: {}", ROOT_CAUSE_CACHE_TABLE, username);
        })
        .doOnComplete(() ->
            log.info("Successfully created ClickHouse user and policies for project: {}", projectId)
        )
        .doOnError(error ->
            log.error("Failed to create ClickHouse user: projectId={}, error={}",
                projectId, error.getMessage(), error)
        );
  }

  // ==================== COMBINED OPERATIONS (Backward Compatible) ====================

    /**
     * Complete setup for a project's ClickHouse access with audit logging.
     * Generates credentials, creates CH user/policies, and saves credentials to MySQL.
     *
     * Note: For transactional flows, use generateUsername(), generatePassword(),
     * save credentials via DAO with SqlConnection, then call createClickhouseUserAndPolicies().
     */
    public Completable setupProjectClickhouseUser(String projectId, String performedBy) {
        String username = generateUsername(projectId);
        String password = generatePassword();

        JsonObject auditDetails = new JsonObject()
            .put("clickhouseUsername", username)
            .put("action", "ClickHouse credentials created");

        return createClickhouseUserAndPolicies(projectId, username, password)
            .andThen(credentialsDao.saveCredentials(projectId, username, password).ignoreElement())
            .andThen(credentialsDao.insertAuditLog(projectId, ProjectAuditAction.CREDENTIALS_SETUP, performedBy, auditDetails))
            .doOnComplete(() ->
                log.info("Successfully set up ClickHouse access for project: {}", projectId)
            )
            .doOnError(error ->
                log.error("Failed to setup ClickHouse user: projectId={}, error={}",
                    projectId, error.getMessage(), error)
            );
    }

    public Completable removeProjectClickhouseUser(String projectId, String performedBy) {
        String username = generateUsername(projectId);

        log.info("Removing ClickHouse user for project: {}, username: {}", projectId, username);

        JsonObject auditDetails = new JsonObject()
            .put("clickhouseUsername", username)
            .put("action", "ClickHouse credentials removed");

        return Completable.fromAction(() -> {
            ConnectionPool adminPool = poolManager.getAdminPool();
            String onCluster = poolManager.getOnClusterClause();

            String policyName = generatePolicyName(projectId);
            String dropPolicySQL = String.format(
                "DROP ROW POLICY IF EXISTS %s%s ON %s",
                policyName, onCluster, OTEL_DB_ALL_TABLES
            );
            executeSQL(adminPool, dropPolicySQL);

            // Legacy: per-table policy on root_cause_cache when column was project_id (pre-ProjectId).
            String rootCausePolicyName = generatePolicyName(projectId, ROOT_CAUSE_CACHE_TABLE);
            String dropRootCausePolicySQL = String.format(
                "DROP ROW POLICY IF EXISTS %s%s ON %s",
                rootCausePolicyName, onCluster, ROOT_CAUSE_CACHE_TABLE
            );
            executeSQL(adminPool, dropRootCausePolicySQL);

            // Drop user
            String dropUserSQL = String.format("DROP USER IF EXISTS %s%s", username, onCluster);
            executeSQL(adminPool, dropUserSQL);

            log.info("Removed ClickHouse user: {}", username);
        })
        .andThen(credentialsDao.deactivateCredentials(projectId))
        .andThen(Completable.fromAction(() -> poolManager.closePoolForProject(projectId)))
        .andThen(credentialsDao.insertAuditLog(projectId, ProjectAuditAction.CREDENTIALS_REMOVED, performedBy, auditDetails))
        .doOnComplete(() ->
            log.info("Successfully removed ClickHouse access for project: {}", projectId)
        )
        .doOnError(error ->
            log.error("Failed to remove ClickHouse user: projectId={}", projectId, error)
        );
    }

    /**
     * Rotate ClickHouse password for a project.
     * Generates new password, updates CH user, updates MySQL credentials, and logs audit.
     */
    public Completable rotateProjectClickhousePassword(String projectId, String performedBy) {
        String username = generateUsername(projectId);
        String newPassword = generatePassword();

        log.info("Rotating ClickHouse password for project: {}, username: {}", projectId, username);

        JsonObject auditDetails = new JsonObject()
            .put("clickhouseUsername", username)
            .put("action", "ClickHouse password rotated")
            .put("reason", "Manual rotation");

        return Completable.fromAction(() -> {
            ConnectionPool adminPool = poolManager.getAdminPool();
            String onCluster = poolManager.getOnClusterClause();

            // Update ClickHouse user password
            String alterUserSQL = String.format(
                "ALTER USER %s%s IDENTIFIED WITH sha256_password BY '%s'",
                username, onCluster, newPassword
            );
            executeSQL(adminPool, alterUserSQL);
            log.info("Updated ClickHouse password for user: {}", username);
        })
        .andThen(credentialsDao.saveCredentials(projectId, username, newPassword).ignoreElement())
        .andThen(Completable.fromAction(() -> {
            // Close and recreate connection pool with new credentials
            poolManager.closePoolForProject(projectId);
            log.info("Closed connection pool for project: {}", projectId);
        }))
        .andThen(credentialsDao.insertAuditLog(projectId, ProjectAuditAction.CREDENTIALS_ROTATED, performedBy, auditDetails))
        .doOnComplete(() ->
            log.info("Successfully rotated password for project: {}", projectId)
        )
        .doOnError(error ->
            log.error("Failed to rotate password: projectId={}", projectId, error)
        );
    }

    /**
     * Get audit history for a project's credentials.
     */
    public Flowable<ClickhouseProjectCredentialAudit> getAuditHistory(String projectId) {
        return credentialsDao.getAuditLogsByProjectId(projectId)
            .doOnError(error -> log.error("Failed to get audit history for project: {}", projectId, error));
    }

    /**
     * Get recent audit logs across all projects.
     */
    public Flowable<ClickhouseProjectCredentialAudit> getRecentAuditLogs(int limit) {
        return credentialsDao.getRecentAuditLogs(limit)
            .doOnError(error -> log.error("Failed to get recent audit logs", error));
    }

    /**
     * Get credentials by project ID (for info display, not actual password).
     */
    public io.reactivex.rxjava3.core.Maybe<org.dreamhorizon.pulseserver.model.ClickhouseProjectCredentials> getCredentialsByProjectId(String projectId) {
        return credentialsDao.getCredentialsByProjectId(projectId);
    }

    // ==================== PRIVATE HELPERS ====================

  private String generatePolicyName(String projectId) {
    return generatePolicyName(projectId, null);
  }

  private String generatePolicyName(String projectId, String tableName) {
    String sanitized = projectId.replace("-", "_").replace("proj_", "");
    if (tableName != null && !tableName.isBlank()) {
      String tableSuffix = tableName.replace("otel.", "").replace(".", "_");
      return "policy_" + sanitized + "_" + tableSuffix;
    }
    return "policy_" + sanitized;
  }

  private void executeSQL(ConnectionPool adminPool, String sql) {
    Connection connection = null;
    try {
      connection = Mono.from(adminPool.create()).block();
      if (connection != null) {
        Mono.from(connection.createStatement(sql)
                .execute())
            .block();
      }
    } catch (Exception e) {
      log.error("Failed to execute SQL: {}", sql, e);
      throw new RuntimeException("SQL execution failed: " + e.getMessage(), e);
    } finally {
      if (connection != null) {
        try {
          Mono.from(connection.close()).block();
        } catch (Exception e) {
          log.warn("Failed to close connection", e);
        }
      }
    }
  }

  // ==================== NESTED CLASSES ====================

  /**
   * Result of saving ClickHouse credentials.
   * Contains the plain password needed for async CH user creation.
   */
  @Getter
  @RequiredArgsConstructor
  public static class CredentialsResult {
    private final String projectId;
    private final String username;
    private final String plainPassword;
  }
}
