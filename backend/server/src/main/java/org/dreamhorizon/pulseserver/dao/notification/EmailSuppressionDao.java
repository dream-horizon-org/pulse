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
import org.dreamhorizon.pulseserver.service.notification.models.EmailSuppression;
import org.dreamhorizon.pulseserver.service.notification.models.SuppressionReason;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class EmailSuppressionDao {

  private final MysqlClient mysqlClient;

  public Maybe<EmailSuppression> getSuppressionByEmail(String projectId, String email) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_SUPPRESSION_BY_EMAIL)
        .rxExecute(Tuple.of(projectId, email.toLowerCase()))
        .flatMapMaybe(
            rows -> {
              var iterator = rows.iterator();
              if (iterator.hasNext()) {
                return Maybe.just(mapRowToSuppression(iterator.next()));
              }
              return Maybe.empty();
            });
  }

  public Single<Boolean> isEmailSuppressed(String projectId, String email) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.IS_EMAIL_SUPPRESSED)
        .rxExecute(Tuple.of(projectId, email.toLowerCase()))
        .map(rows -> rows.iterator().hasNext());
  }

  public Single<Long> addToSuppressionList(
      String projectId,
      String email,
      SuppressionReason reason,
      String bounceType,
      String sourceMessageId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(NotificationQueries.INSERT_SUPPRESSION)
        .rxExecute(
            Tuple.of(projectId, email.toLowerCase(), reason.name(), bounceType, sourceMessageId))
        .map(rows -> rows.property(MySQLClient.LAST_INSERTED_ID));
  }

  public Single<Boolean> addToSuppressionListAllProjects(
      String email, SuppressionReason reason, String bounceType, String sourceMessageId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(NotificationQueries.INSERT_SUPPRESSION_ALL_PROJECTS)
        .rxExecute(Tuple.of(email.toLowerCase(), reason.name(), bounceType, sourceMessageId))
        .map(rows -> rows.rowCount() > 0);
  }

  public Single<Integer> removeFromSuppressionList(String projectId, String email) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(NotificationQueries.DELETE_SUPPRESSION)
        .rxExecute(Tuple.of(projectId, email.toLowerCase()))
        .map(rows -> rows.rowCount());
  }

  public Single<List<EmailSuppression>> getSuppressionsByProject(
      String projectId, int limit, int offset) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_SUPPRESSIONS_BY_PROJECT)
        .rxExecute(Tuple.of(projectId, limit, offset))
        .map(
            rows -> {
              List<EmailSuppression> result = new ArrayList<>();
              rows.forEach(row -> result.add(mapRowToSuppression(row)));
              return result;
            });
  }

  private EmailSuppression mapRowToSuppression(Row row) {
    return EmailSuppression.builder()
        .id(row.getLong("id"))
        .projectId(row.getString("project_id"))
        .email(row.getString("email"))
        .reason(SuppressionReason.valueOf(row.getString("reason")))
        .bounceType(row.getString("bounce_type"))
        .sourceMessageId(row.getString("source_message_id"))
        .suppressedAt(
            row.getLocalDateTime("suppressed_at") != null
                ? row.getLocalDateTime("suppressed_at").toInstant(ZoneOffset.UTC)
                : null)
        .build();
  }
}
