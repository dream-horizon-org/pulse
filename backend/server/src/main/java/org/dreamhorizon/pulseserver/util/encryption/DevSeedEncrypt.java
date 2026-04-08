package org.dreamhorizon.pulseserver.util.encryption;

import java.util.Objects;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;

/**
 * One-off utility to generate encrypted ClickHouse password for dev seed (e.g. default-project).
 * Run from backend/server with: ENCRYPTION_MASTER_KEY=base64key [args...]
 * Args: [projectId] [clickhouseUsername] [plainPassword]
 * Prints an SQL INSERT statement for clickhouse_project_credentials to stdout.
 */
public final class DevSeedEncrypt {

  private DevSeedEncrypt() {}

  public static void main(String[] args) {
    String key = System.getenv("ENCRYPTION_MASTER_KEY");
    if (key == null || key.isBlank()) {
      System.err.println("ENCRYPTION_MASTER_KEY must be set (e.g. from deploy/.env)");
      System.exit(1);
    }
    String projectId = args.length > 0 ? args[0] : "default-project";
    String username = args.length > 1 ? args[1] : "pulse_user";
    String plainPassword = args.length > 2 ? args[2] : "pulse_password";

    ApplicationConfig config = new ApplicationConfig();
    config.setEncryptionMasterKey(key);
    ClickhousePasswordEncryptionUtil util = new ClickhousePasswordEncryptionUtil(config);
    EncryptedData encrypted = util.encrypt(plainPassword);

    String enc = escapeSql(encrypted.getEncryptedValue());
    String salt = escapeSql(encrypted.getSalt());
    String digest = escapeSql(encrypted.getDigest());

    System.out.println(
        "INSERT INTO clickhouse_project_credentials "
            + "(project_id, clickhouse_username, clickhouse_password_encrypted, encryption_salt, password_digest, is_active) "
            + "VALUES ('" + projectId + "', '" + username + "', '" + enc + "', '" + salt + "', '" + digest + "', TRUE) "
            + "ON DUPLICATE KEY UPDATE clickhouse_username=VALUES(clickhouse_username), "
            + "clickhouse_password_encrypted=VALUES(clickhouse_password_encrypted), "
            + "encryption_salt=VALUES(encryption_salt), password_digest=VALUES(password_digest), is_active=TRUE;");
  }

  private static String escapeSql(String s) {
    return Objects.requireNonNull(s).replace("\\", "\\\\").replace("'", "''");
  }
}
