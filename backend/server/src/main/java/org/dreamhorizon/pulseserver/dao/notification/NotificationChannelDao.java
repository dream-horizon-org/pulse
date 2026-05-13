package org.dreamhorizon.pulseserver.dao.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.SqlResult;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationChannel;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class NotificationChannelDao {

  private final MysqlClient mysqlClient;
  private final ObjectMapper objectMapper;

  public Maybe<NotificationChannel> getChannelById(Long channelId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_CHANNEL_BY_ID)
        .rxExecute(Tuple.of(channelId))
        .flatMapMaybe(
            rows -> {
              var iterator = rows.iterator();
              if (iterator.hasNext()) {
                return Maybe.just(mapRowToChannel(iterator.next()));
              }
              return Maybe.empty();
            });
  }

  public Single<List<NotificationChannel>> getChannelsByProject(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_CHANNELS_BY_PROJECT)
        .rxExecute(Tuple.of(projectId))
        .map(
            rows -> {
              List<NotificationChannel> result = new ArrayList<>();
              rows.forEach(row -> result.add(mapRowToChannel(row)));
              return result;
            });
  }

  public Single<List<NotificationChannel>> getChannelsAccessibleByProject(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_CHANNELS_ACCESSIBLE_BY_PROJECT)
        .rxExecute(Tuple.of(projectId))
        .map(
            rows -> {
              List<NotificationChannel> result = new ArrayList<>();
              rows.forEach(row -> result.add(mapRowToChannel(row)));
              return result;
            });
  }

  public Single<List<NotificationChannel>> getChannelsAccessibleByProjectAndType(
      String projectId, ChannelType channelType) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_CHANNELS_ACCESSIBLE_BY_PROJECT_AND_TYPE)
        .rxExecute(Tuple.of(projectId, channelType.name()))
        .map(
            rows -> {
              List<NotificationChannel> result = new ArrayList<>();
              rows.forEach(row -> result.add(mapRowToChannel(row)));
              return result;
            });
  }

  public Single<List<NotificationChannel>> getSharedChannels() {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_SHARED_CHANNELS)
        .rxExecute()
        .map(
            rows -> {
              List<NotificationChannel> result = new ArrayList<>();
              rows.forEach(row -> result.add(mapRowToChannel(row)));
              return result;
            });
  }

  public Single<List<NotificationChannel>> getActiveChannelsByType(
      String projectId, ChannelType channelType) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_ACTIVE_CHANNELS_BY_TYPE)
        .rxExecute(Tuple.of(projectId, channelType.name()))
        .map(
            rows -> {
              List<NotificationChannel> result = new ArrayList<>();
              rows.forEach(row -> result.add(mapRowToChannel(row)));
              return result;
            });
  }

  public Maybe<NotificationChannel> getActiveChannelByType(
      String projectId, ChannelType channelType) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_ACTIVE_CHANNELS_BY_TYPE)
        .rxExecute(Tuple.of(projectId, channelType.name()))
        .flatMapMaybe(
            rows -> {
              var iterator = rows.iterator();
              if (iterator.hasNext()) {
                return Maybe.just(mapRowToChannel(iterator.next()));
              }
              return Maybe.empty();
            });
  }

  public Maybe<NotificationChannel> getActiveChannelByProjectAndType(
      String projectId, ChannelType channelType) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_ACTIVE_CHANNEL_BY_PROJECT_AND_TYPE)
        .rxExecute(Tuple.of(projectId, channelType.name()))
        .flatMapMaybe(
            rows -> {
              var iterator = rows.iterator();
              if (iterator.hasNext()) {
                return Maybe.just(mapRowToChannel(iterator.next()));
              }
              return Maybe.empty();
            });
  }

  public Single<Long> createChannel(NotificationChannel channel) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(NotificationQueries.INSERT_CHANNEL)
        .rxExecute(
            Tuple.of(
                channel.getProjectId(),
                channel.getChannelType().name(),
                channel.getName(),
                toJsonObject(channel.getConfig()),
                channel.getIsActive() != null ? channel.getIsActive() : true))
        .map(rows -> rows.property(MySQLClient.LAST_INSERTED_ID));
  }

  public Single<Integer> updateChannel(Long channelId, NotificationChannel channel) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(NotificationQueries.UPDATE_CHANNEL)
        .rxExecute(
            Tuple.of(channel.getName(), toJsonObject(channel.getConfig()), channel.getIsActive(), channelId))
        .map(SqlResult::rowCount);
  }

  public Single<Integer> deleteChannel(Long channelId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(NotificationQueries.DELETE_CHANNEL)
        .rxExecute(Tuple.of(channelId))
        .map(SqlResult::rowCount);
  }

  private NotificationChannel mapRowToChannel(Row row) {
    Object configValue = row.getValue("config");
    ChannelConfig config = configValue instanceof JsonObject jsonObject
        ? objectMapper.convertValue(jsonObject.getMap(), ChannelConfig.class)
        : null;

    return NotificationChannel.builder()
        .id(row.getLong("id"))
        .projectId(row.getString("project_id"))
        .channelType(ChannelType.valueOf(row.getString("channel_type")))
        .name(row.getString("name"))
        .config(config)
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

  @SuppressWarnings("unchecked")
  private JsonObject toJsonObject(ChannelConfig config) {
    if (config == null) {
      return null;
    }
    return new JsonObject(objectMapper.convertValue(config, Map.class));
  }
}
