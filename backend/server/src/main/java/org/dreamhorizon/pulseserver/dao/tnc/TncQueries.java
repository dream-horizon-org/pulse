package org.dreamhorizon.pulseserver.dao.tnc;

public class TncQueries {

  public static final String GET_ACTIVE_VERSION =
      "SELECT id, version, tos_s3_url, aup_s3_url, privacy_policy_s3_url, summary, "
          + "is_active, published_at, created_by, created_at "
          + "FROM tnc_versions WHERE is_active = TRUE ORDER BY id DESC LIMIT 1";

  public static final String GET_ACCEPTANCE =
      "SELECT id, tenant_id, tnc_version_id, accepted_by_email, accepted_at, user_agent "
          + "FROM tnc_acceptances WHERE tenant_id = ? AND tnc_version_id = ?";

  public static final String INSERT_ACCEPTANCE =
      "INSERT INTO tnc_acceptances (tenant_id, tnc_version_id, accepted_by_email, user_agent) "
          + "VALUES (?, ?, ?, ?) "
          + "ON DUPLICATE KEY UPDATE accepted_by_email = VALUES(accepted_by_email), "
          + "accepted_at = CURRENT_TIMESTAMP, user_agent = VALUES(user_agent)";

  public static final String GET_ACCEPTANCE_HISTORY =
      "SELECT a.id, a.tenant_id, a.tnc_version_id, a.accepted_by_email, a.accepted_at, "
          + "a.user_agent "
          + "FROM tnc_acceptances a "
          + "WHERE a.tenant_id = ? ORDER BY a.accepted_at DESC";

  public static final String DEACTIVATE_OTHER_VERSIONS =
      "UPDATE tnc_versions SET is_active = FALSE WHERE version != ?";

  public static final String INSERT_VERSION =
      "INSERT INTO tnc_versions (version, tos_s3_url, aup_s3_url, privacy_policy_s3_url, summary, is_active, created_by) "
          + "VALUES (?, ?, ?, ?, ?, TRUE, ?)";
}
