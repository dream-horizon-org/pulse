package org.dreamhorizon.pulseserver.dao.spark;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.SqlResult;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;

/**
 * DAO for managing Spark job records.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SparkJobDao {

  /**
   * MySQL client.
   */
  private final MysqlClient mysqlClient;

  /**
   * Inserts a new Spark job record.
   *
   * @param jobType     type of job
   * @param referenceId ID of the reference entity
   * @param jobId       EMR job run ID
   * @param status      job status
   * @return the ID of the inserted record
   */
  public Single<Long> insertJob(
      final SparkJobType jobType,
      final Long referenceId,
      final String jobId,
      final SparkJobStatus status) {
    String query = "INSERT INTO spark_jobs "
        + "(job_type, reference_id, job_id, status) "
        + "VALUES (?, ?, ?, ?)";
    Tuple params = Tuple.of(jobType.name(), referenceId, jobId, status.name());

    return mysqlClient.getWriterPool().preparedQuery(query)
        .rxExecute(params)
        .map(r -> r.property(MySQLClient.LAST_INSERTED_ID));
  }

  /**
   * Updates the EMR job run ID and status of a Spark job by its database ID.
   *
   * @param id     database ID
   * @param jobId  EMR job run ID
   * @param status new status
   * @return the number of rows updated
   */
  public Single<Integer> updateJobIdAndStatus(
      final Long id,
      final String jobId,
      final SparkJobStatus status) {
    String query = "UPDATE spark_jobs SET job_id = ?, status = ? WHERE id = ?";
    Tuple params = Tuple.of(jobId, status.name(), id);

    return mysqlClient.getWriterPool().preparedQuery(query)
        .rxExecute(params)
        .map(SqlResult::rowCount);
  }

  /**
   * Updates the status of a Spark job by its database ID.
   *
   * @param id           database ID
   * @param status       new status
   * @param errorMessage error message if failed
   * @param startedAt    time the job started
   * @param completedAt  time the job completed
   * @return the number of rows updated
   */
  public Single<Integer> updateJobStatus(
      final Long id,
      final SparkJobStatus status,
      final String errorMessage,
      final LocalDateTime startedAt,
      final LocalDateTime completedAt) {
    String query = "UPDATE spark_jobs SET status = ?, "
        + "error_message = ?, started_at = ?, completed_at = ? "
        + "WHERE id = ?";
    Tuple params = Tuple.of(
        status.name(), errorMessage, startedAt, completedAt, id);

    return mysqlClient.getWriterPool().preparedQuery(query)
        .rxExecute(params)
        .map(SqlResult::rowCount);
  }

  /**
   * Updates the status of a Spark job by its EMR job run ID.
   *
   * @param jobId        EMR job run ID
   * @param status       new status
   * @param errorMessage error message if failed
   * @param startedAt    time the job started
   * @param completedAt  time the job completed
   * @return the number of rows updated
   */
  public Single<Integer> updateJobStatusByJobId(
      final String jobId,
      final SparkJobStatus status,
      final String errorMessage,
      final LocalDateTime startedAt,
      final LocalDateTime completedAt) {
    String query = "UPDATE spark_jobs SET status = ?, "
        + "error_message = ?, started_at = ?, completed_at = ? "
        + "WHERE job_id = ?";
    Tuple params = Tuple.of(
        status.name(), errorMessage, startedAt, completedAt, jobId);

    return mysqlClient.getWriterPool().preparedQuery(query)
        .rxExecute(params)
        .map(SqlResult::rowCount);
  }

  /**
   * Retrieves a Spark job by its database ID.
   *
   * @param id database ID
   * @return the Spark job entity if found
   */
  public Maybe<SparkJobEntity> getJobById(final Long id) {
    String query = "SELECT * FROM spark_jobs WHERE id = ?";
    return mysqlClient.getWriterPool().preparedQuery(query)
        .rxExecute(Tuple.of(id))
        .flatMapMaybe(rows -> {
          if (rows.iterator().hasNext()) {
            return Maybe.just(mapRowToEntity(rows.iterator().next()));
          }
          return Maybe.empty();
        });
  }

  /**
   * Retrieves a Spark job by its EMR job run ID.
   *
   * @param jobId EMR job run ID
   * @return the Spark job entity if found
   */
  public Maybe<SparkJobEntity> getJobByJobId(final String jobId) {
    String query = "SELECT * FROM spark_jobs WHERE job_id = ?";
    return mysqlClient.getWriterPool().preparedQuery(query)
        .rxExecute(Tuple.of(jobId))
        .flatMapMaybe(rows -> {
          if (rows.iterator().hasNext()) {
            return Maybe.just(mapRowToEntity(rows.iterator().next()));
          }
          return Maybe.empty();
        });
  }

  /**
   * Retrieves the latest Spark job for a given reference entity.
   *
   * @param jobType     type of job
   * @param referenceId ID of the reference entity
   * @return the latest Spark job entity if found
   */
  public Maybe<SparkJobEntity> getLatestJobByReference(
      final SparkJobType jobType,
      final Long referenceId) {
    String query = "SELECT * FROM spark_jobs WHERE job_type = ? "
        + "AND reference_id = ? "
        + "ORDER BY created_at DESC LIMIT 1";
    return mysqlClient.getWriterPool().preparedQuery(
            query)
        .rxExecute(Tuple.of(jobType.name(), referenceId))
        .flatMapMaybe(rows -> {
          if (rows.iterator().hasNext()) {
            return Maybe.just(mapRowToEntity(rows.iterator().next()));
          }
          return Maybe.empty();
        });
  }

  /**
   * Retrieves the latest Spark job for a given job type.
   *
   * @param jobType     type of job
   * @return the latest Spark job entity if found
   */
  public Maybe<SparkJobEntity> getLatestJobByType(
      final SparkJobType jobType) {
    String query = "SELECT * FROM spark_jobs WHERE job_type = ? "
        + "ORDER BY created_at DESC LIMIT 1";
    return mysqlClient.getWriterPool().preparedQuery(query)
        .rxExecute(Tuple.of(jobType.name()))
        .flatMapMaybe(rows -> {
          if (rows.iterator().hasNext()) {
            return Maybe.just(mapRowToEntity(rows.iterator().next()));
          }
          return Maybe.empty();
        });
  }

  private SparkJobEntity mapRowToEntity(final Row row) {
    return SparkJobEntity.builder()
        .id(row.getLong("id"))
        .jobType(SparkJobType.valueOf(row.getString("job_type")))
        .referenceId(row.getLong("reference_id"))
        .jobId(row.getString("job_id"))
        .status(SparkJobStatus.valueOf(row.getString("status")))
        .errorMessage(row.getString("error_message"))
        .startedAt(row.getLocalDateTime("started_at"))
        .completedAt(row.getLocalDateTime("completed_at"))
        .createdAt(row.getLocalDateTime("created_at"))
        .build();
  }
}
