package org.dreamhorizon.pulseserver.dao.cronjobhistory;

public final class CronJobHistoryQueries {

  public static final String FAIL_STALE_IN_PROGRESS = """
      UPDATE cron_jobs_history
      SET status = 'FAILED',
          completed_at = CURRENT_TIMESTAMP(3),
          updated_at = CURRENT_TIMESTAMP(3),
          error_message = ?
      WHERE job_type = ?
        AND status = 'IN_PROGRESS'
        AND started_at < ?
      """;

  public static final String INSERT_IF_NO_ACTIVE_IN_PROGRESS = """
      INSERT INTO cron_jobs_history (job_type, status, started_at, completed_at, updated_at, error_message)
      SELECT ?, 'IN_PROGRESS', CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3), NULL
      WHERE NOT EXISTS (
        SELECT 1 FROM cron_jobs_history c
        WHERE c.job_type = ?
          AND c.status = 'IN_PROGRESS'
          AND c.started_at >= ?
      )
      """;

  public static final String SELECT_ACTIVE_IN_PROGRESS_ID = """
      SELECT id FROM cron_jobs_history
      WHERE job_type = ?
        AND status = 'IN_PROGRESS'
        AND started_at >= ?
      ORDER BY id DESC
      LIMIT 1
      """;

  public static final String MARK_COMPLETED = """
      UPDATE cron_jobs_history
      SET status = 'COMPLETED',
          completed_at = CURRENT_TIMESTAMP(3),
          updated_at = CURRENT_TIMESTAMP(3)
      WHERE id = ?
        AND status = 'IN_PROGRESS'
      """;

  public static final String MARK_FAILED = """
      UPDATE cron_jobs_history
      SET status = 'FAILED',
          completed_at = CURRENT_TIMESTAMP(3),
          updated_at = CURRENT_TIMESTAMP(3),
          error_message = ?
      WHERE id = ?
        AND status = 'IN_PROGRESS'
      """;

  private CronJobHistoryQueries() {
  }
}
