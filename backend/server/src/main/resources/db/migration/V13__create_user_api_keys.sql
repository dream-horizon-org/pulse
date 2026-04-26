CREATE TABLE user_api_keys (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id          VARCHAR(255) NOT NULL,
  display_name     VARCHAR(128) NOT NULL,
  api_key_hash     VARCHAR(512) NOT NULL,
  key_prefix       VARCHAR(24)  NOT NULL,
  is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at       TIMESTAMP    NULL,
  revoked_by       VARCHAR(255) NULL,

  CONSTRAINT fk_user_api_keys_user
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,

  UNIQUE KEY uk_user_api_key_hash (api_key_hash),
  KEY idx_user_api_key_user (user_id),
  KEY idx_user_api_key_active (user_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
