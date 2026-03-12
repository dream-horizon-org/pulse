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
import org.dreamhorizon.pulseserver.service.notification.models.ChannelEventMapping;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ChannelEventMappingDao {

  private final MysqlClient mysqlClient;

  public Maybe<ChannelEventMapping> getMappingById(Long mappingId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_MAPPING_BY_ID)
        .rxExecute(Tuple.of(mappingId))
        .flatMapMaybe(
            rows -> {
              var iterator = rows.iterator();
              if (iterator.hasNext()) {
                return Maybe.just(mapRowToMapping(iterator.next()));
              }
              return Maybe.empty();
            });
  }

  public Single<List<ChannelEventMapping>> getMappingsByProject(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_MAPPINGS_BY_PROJECT)
        .rxExecute(Tuple.of(projectId))
        .map(
            rows -> {
              List<ChannelEventMapping> result = new ArrayList<>();
              rows.forEach(row -> result.add(mapRowToMapping(row)));
              return result;
            });
  }

  /**
   * Returns a single active mapping by ID, joined with channel info.
   * Row includes extra columns: channel_type, config, channel_name, channel_active.
   */
  public Maybe<Row> getActiveMappingWithChannelById(Long mappingId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_ACTIVE_MAPPING_WITH_CHANNEL_BY_ID)
        .rxExecute(Tuple.of(mappingId))
        .flatMapMaybe(
            rows -> {
              var iterator = rows.iterator();
              if (iterator.hasNext()) {
                return Maybe.just(iterator.next());
              }
              return Maybe.empty();
            });
  }

  /**
   * Returns active mappings for a project+event, joined with channel info.
   * Row includes extra columns: channel_type, config, channel_name, channel_active.
   */
  public Single<List<Row>> getActiveMappingsWithChannelByProjectAndEvent(
      String projectId, String eventName) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_ACTIVE_MAPPINGS_BY_PROJECT_AND_EVENT)
        .rxExecute(Tuple.of(projectId, eventName))
        .map(
            rows -> {
              List<Row> result = new ArrayList<>();
              rows.forEach(result::add);
              return result;
            });
  }

  public Single<Long> createMapping(ChannelEventMapping mapping) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(NotificationQueries.INSERT_MAPPING)
        .rxExecute(
            Tuple.of(
                mapping.getProjectId(),
                mapping.getChannelId(),
                mapping.getEventName(),
                mapping.getRecipient(),
                mapping.getRecipientName(),
                mapping.getIsActive() != null ? mapping.getIsActive() : true))
        .map(rows -> rows.property(MySQLClient.LAST_INSERTED_ID));
  }

  public Single<Integer> updateMapping(Long mappingId, ChannelEventMapping mapping) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(NotificationQueries.UPDATE_MAPPING)
        .rxExecute(
            Tuple.of(
                mapping.getRecipient(),
                mapping.getRecipientName(),
                mapping.getIsActive(),
                mappingId))
        .map(rows -> rows.rowCount());
  }

  public Single<Integer> deleteMapping(Long mappingId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(NotificationQueries.DELETE_MAPPING)
        .rxExecute(Tuple.of(mappingId))
        .map(rows -> rows.rowCount());
  }

  private ChannelEventMapping mapRowToMapping(Row row) {
    return ChannelEventMapping.builder()
        .id(row.getLong("id"))
        .projectId(row.getString("project_id"))
        .channelId(row.getLong("channel_id"))
        .eventName(row.getString("event_name"))
        .recipient(row.getString("recipient"))
        .recipientName(row.getString("recipient_name"))
        .isActive(row.getBoolean("is_active"))
        .createdAt(
            row.getLocalDateTime("created_at") != null
                ? row.getLocalDateTime("created_at").toInstant(ZoneOffset.UTC)
                : null)
        .updatedAt(
            row.getLocalDateTime("updated_at") != null
                ? row.getLocalDateTime("updated_at").toInstant(ZoneOffset.UTC)
                : null)
        .build();
  }
}
