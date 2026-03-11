package org.dreamhorizon.pulseserver.dao.notification;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationLog;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationStatus;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class NotificationLogDao {

  private final MysqlClient mysqlClient;

  public Single<List<NotificationLog>> getLogsByProject(String projectId, int limit, int offset) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_LOGS_BY_PROJECT)
        .rxExecute(Tuple.of(projectId, limit, offset))
        .map(
            rows -> {
              List<NotificationLog> result = new ArrayList<>();
              rows.forEach(row -> result.add(mapRowToLog(row)));
              return result;
            });
  }

  public Single<List<NotificationLog>> getLogsByIdempotencyKey(
      String projectId, String idempotencyKey) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_LOGS_BY_IDEMPOTENCY_KEY)
        .rxExecute(Tuple.of(projectId, idempotencyKey))
        .map(
            rows -> {
              List<NotificationLog> result = new ArrayList<>();
              rows.forEach(row -> result.add(mapRowToLog(row)));
              return result;
            });
  }

  public Maybe<NotificationLog> getLogByIdempotency(
      String projectId, String idempotencyKey, ChannelType channelType, String recipient) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_LOG_BY_IDEMPOTENCY)
        .rxExecute(Tuple.of(projectId, idempotencyKey, channelType.name(), recipient))
        .flatMapMaybe(
            rows -> {
              var iterator = rows.iterator();
              if (iterator.hasNext()) {
                return Maybe.just(mapRowToLog(iterator.next()));
              }
              return Maybe.empty();
            });
  }

  public Single<Long> insertLog(NotificationLog log) {
    MySQLPool pool = mysqlClient.getWriterPool();
    Tuple tuple =
        Tuple.tuple()
            .addString(log.getProjectId())
            .addString(log.getIdempotencyKey())
            .addString(log.getChannelType().name())
            .addLong(log.getChannelId())
            .addLong(log.getTemplateId())
            .addString(log.getRecipient())
            .addString(log.getSubject())
            .addString(log.getStatus().name())
            .addInteger(log.getAttemptCount());
    return pool.preparedQuery(NotificationQueries.INSERT_LOG)
        .rxExecute(tuple)
        .map(rows -> rows.property(MySQLClient.LAST_INSERTED_ID));
  }

  public Single<Boolean> insertLogIfNotExists(NotificationLog log) {
    MySQLPool pool = mysqlClient.getWriterPool();
    Tuple tuple =
        Tuple.tuple()
            .addString(log.getProjectId())
            .addString(log.getIdempotencyKey())
            .addString(log.getChannelType().name())
            .addLong(log.getChannelId())
            .addLong(log.getTemplateId())
            .addString(log.getRecipient())
            .addString(log.getSubject())
            .addString(log.getStatus().name())
            .addInteger(log.getAttemptCount());
    return pool.preparedQuery(NotificationQueries.INSERT_LOG_IF_NOT_EXISTS)
        .rxExecute(tuple)
        .map(rows -> rows.rowCount() > 0);
  }

  public Single<Integer> updateLogStatus(
      Long logId,
      NotificationStatus status,
      int attemptCount,
      String errorMessage,
      String errorCode,
      String externalId,
      String providerResponse,
      Integer latencyMs) {
    MySQLPool pool = mysqlClient.getWriterPool();
    Tuple tuple =
        Tuple.tuple()
            .addString(status.name())
            .addInteger(attemptCount)
            .addString(errorMessage)
            .addString(errorCode)
            .addString(externalId)
            .addString(providerResponse)
            .addInteger(latencyMs)
            .addString(status.name())
            .addLong(logId);
    return pool.preparedQuery(NotificationQueries.UPDATE_LOG_STATUS)
        .rxExecute(tuple)
        .map(rows -> rows.rowCount());
  }

  public Single<Integer> updateLogStatusByExternalId(
      String externalId, NotificationStatus status, String message) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(NotificationQueries.UPDATE_LOG_BY_EXTERNAL_ID)
        .rxExecute(Tuple.of(status.name(), message, externalId))
        .map(rows -> rows.rowCount());
  }

  private NotificationLog mapRowToLog(Row row) {
    return NotificationLog.builder()
        .id(row.getLong("id"))
        .projectId(row.getString("project_id"))
        .idempotencyKey(row.getString("idempotency_key"))
        .channelType(ChannelType.valueOf(row.getString("channel_type")))
        .channelId(row.getLong("channel_id"))
        .templateId(row.getLong("template_id"))
        .recipient(row.getString("recipient"))
        .subject(row.getString("subject"))
        .status(NotificationStatus.valueOf(row.getString("status")))
        .attemptCount(row.getInteger("attempt_count"))
        .lastAttemptAt(
            row.getLocalDateTime("last_attempt_at") != null
                ? row.getLocalDateTime("last_attempt_at").toInstant(ZoneOffset.UTC)
                : null)
        .errorMessage(row.getString("error_message"))
        .errorCode(row.getString("error_code"))
        .externalId(row.getString("external_id"))
        .providerResponse(row.getString("provider_response"))
        .latencyMs(row.getInteger("latency_ms"))
        .createdAt(
            row.getLocalDateTime("created_at") != null
                ? row.getLocalDateTime("created_at").toInstant(ZoneOffset.UTC)
                : null)
        .sentAt(
            row.getLocalDateTime("sent_at") != null
                ? row.getLocalDateTime("sent_at").toInstant(ZoneOffset.UTC)
                : null)
        .build();
  }
}
