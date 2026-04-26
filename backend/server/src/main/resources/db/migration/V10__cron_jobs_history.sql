CREATE TABLE cron_jobs_history (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  job_type VARCHAR(32) NOT NULL,
  status ENUM('IN_PROGRESS', 'COMPLETED', 'FAILED') NOT NULL,
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  error_message TEXT NULL,
  PRIMARY KEY (id),
  KEY idx_job_type_status_started (job_type, status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
