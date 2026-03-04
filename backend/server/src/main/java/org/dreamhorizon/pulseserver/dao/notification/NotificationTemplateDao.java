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
import org.dreamhorizon.pulseserver.service.notification.models.NotificationTemplate;
import org.dreamhorizon.pulseserver.tenant.TenantContext;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class NotificationTemplateDao {

  private final MysqlClient mysqlClient;

  public Maybe<NotificationTemplate> getTemplateById(Long templateId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_TEMPLATE_BY_ID)
        .rxExecute(Tuple.of(templateId))
        .flatMapMaybe(
            rows -> {
              var iterator = rows.iterator();
              if (iterator.hasNext()) {
                return Maybe.just(mapRowToTemplate(iterator.next()));
              }
              return Maybe.empty();
            });
  }

  public Single<List<NotificationTemplate>> getTemplatesByProject(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_TEMPLATES_BY_PROJECT)
        .rxExecute(Tuple.of(projectId))
        .map(
            rows -> {
              List<NotificationTemplate> result = new ArrayList<>();
              rows.forEach(row -> result.add(mapRowToTemplate(row)));
              return result;
            });
  }

  public Maybe<NotificationTemplate> getTemplateByEventNameAndChannel(
      String projectId, String eventName, ChannelType channelType) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_TEMPLATE_BY_EVENT_NAME_AND_CHANNEL)
        .rxExecute(Tuple.of(projectId, eventName, channelType != null ? channelType.name() : null))
        .flatMapMaybe(
            rows -> {
              var iterator = rows.iterator();
              if (iterator.hasNext()) {
                return Maybe.just(mapRowToTemplate(iterator.next()));
              }
              return Maybe.empty();
            });
  }

  public Single<Integer> getLatestVersion(
      String projectId, String eventName, ChannelType channelType) {
    String channelTypeStr = channelType != null ? channelType.name() : null;
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(NotificationQueries.GET_LATEST_TEMPLATE_VERSION)
        .rxExecute(Tuple.of(projectId, eventName, channelTypeStr, channelTypeStr))
        .map(
            rows -> {
              var iterator = rows.iterator();
              if (iterator.hasNext()) {
                Integer version = iterator.next().getInteger(0);
                return version != null ? version : 0;
              }
              return 0;
            });
  }

  public Single<Long> createTemplate(NotificationTemplate template) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(NotificationQueries.INSERT_TEMPLATE)
        .rxExecute(
            Tuple.of(
                template.getProjectId(),
                template.getEventName(),
                template.getChannelType() != null ? template.getChannelType().name() : null,
                template.getVersion(),
                template.getBody(),
                template.getIsActive() != null ? template.getIsActive() : true))
        .map(rows -> rows.property(MySQLClient.LAST_INSERTED_ID));
  }

  public Single<Integer> updateTemplate(Long templateId, NotificationTemplate template) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(NotificationQueries.UPDATE_TEMPLATE)
        .rxExecute(
            Tuple.of(
                template.getEventName(),
                template.getChannelType() != null ? template.getChannelType().name() : null,
                template.getBody(),
                template.getIsActive(),
                templateId))
        .map(rows -> rows.rowCount());
  }

  public Single<Integer> deleteTemplate(Long templateId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(NotificationQueries.DELETE_TEMPLATE)
        .rxExecute(Tuple.of(templateId))
        .map(rows -> rows.rowCount());
  }

  private NotificationTemplate mapRowToTemplate(Row row) {
    String channelTypeStr = row.getString("channel_type");
    Object bodyValue = row.getValue("body");
    String bodyStr = bodyValue != null ? bodyValue.toString() : null;

    return NotificationTemplate.builder()
        .id(row.getLong("id"))
        .projectId(row.getString("project_id"))
        .eventName(row.getString("event_name"))
        .channelType(channelTypeStr != null ? ChannelType.valueOf(channelTypeStr) : null)
        .version(row.getInteger("version"))
        .body(bodyStr)
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
