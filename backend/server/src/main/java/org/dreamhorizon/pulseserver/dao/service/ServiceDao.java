package org.dreamhorizon.pulseserver.dao.service;

import static org.dreamhorizon.pulseserver.dao.service.ServiceQueries.GET_ALL_ACTIVE;
import static org.dreamhorizon.pulseserver.dao.service.ServiceQueries.GET_BY_SERVICE_NAME;
import static org.dreamhorizon.pulseserver.dao.service.ServiceQueries.INSERT;
import static org.dreamhorizon.pulseserver.dao.service.ServiceQueries.SOFT_DELETE;
import static org.dreamhorizon.pulseserver.dao.service.ServiceQueries.UPDATE;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.service.models.ServiceRow;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ServiceDao {
  private final MysqlClient mysqlClient;

  public Maybe<ServiceRow> getByServiceName(String serviceName) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_BY_SERVICE_NAME)
        .rxExecute(Tuple.of(serviceName))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRow(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch service: {}", serviceName, error));
  }

  public Single<List<ServiceRow>> getAllActive() {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_ALL_ACTIVE)
        .rxExecute()
        .map(rowSet -> {
          List<ServiceRow> services = new ArrayList<>();
          for (Row row : rowSet) {
            services.add(mapRow(row));
          }
          return services;
        })
        .doOnError(error -> log.error("Failed to list services", error));
  }

  public Single<ServiceRow> create(ServiceRow service) {
    MySQLPool pool = mysqlClient.getWriterPool();
    Tuple params = Tuple.tuple()
        .addValue(service.getServiceName())
        .addValue(service.getServiceGroup())
        .addValue(service.getDisplayName())
        .addValue(service.getOwnerEmail())
        .addValue(service.getOwnerSlackId())
        .addValue(service.getGoalertServiceId())
        .addValue(service.getDescription());
    return pool.preparedQuery(INSERT)
        .rxExecute(params)
        .map(result -> {
          long id = Long.parseLong(
              result.property(MySQLClient.LAST_INSERTED_ID).toString());
          log.info("Created service: {} (id: {})", service.getServiceName(), id);
          return service.toBuilder().id(id).isActive(true).build();
        })
        .doOnError(error -> log.error("Failed to create service: {}",
            service.getServiceName(), error));
  }

  public Single<ServiceRow> update(String serviceName, ServiceRow service) {
    MySQLPool pool = mysqlClient.getWriterPool();
    Tuple params = Tuple.tuple()
        .addValue(service.getServiceGroup())
        .addValue(service.getDisplayName())
        .addValue(service.getOwnerEmail())
        .addValue(service.getOwnerSlackId())
        .addValue(service.getGoalertServiceId())
        .addValue(service.getDescription())
        .addValue(serviceName);
    return pool.preparedQuery(UPDATE)
        .rxExecute(params)
        .flatMap(result -> {
          if (result.rowCount() == 0) {
            return Single.error(new RuntimeException("Service not found: " + serviceName));
          }
          log.info("Updated service: {}", serviceName);
          return Single.just(service.toBuilder().serviceName(serviceName).build());
        })
        .doOnError(error -> log.error("Failed to update service: {}", serviceName, error));
  }

  public Completable softDelete(String serviceName) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(SOFT_DELETE)
        .rxExecute(Tuple.of(serviceName))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            log.warn("No active service found to delete: {}", serviceName);
          } else {
            log.info("Soft-deleted service: {}", serviceName);
          }
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to delete service: {}", serviceName, error));
  }

  private ServiceRow mapRow(Row row) {
    return ServiceRow.builder()
        .id(row.getLong("id"))
        .serviceName(row.getString("service_name"))
        .serviceGroup(row.getString("service_group"))
        .displayName(row.getString("display_name"))
        .ownerEmail(row.getString("owner_email"))
        .ownerSlackId(row.getString("owner_slack_id"))
        .goalertServiceId(row.getString("goalert_service_id"))
        .description(row.getString("description"))
        .isActive(row.getBoolean("is_active"))
        .createdAt(row.getLocalDateTime("created_at") != null
            ? row.getLocalDateTime("created_at").toString() : null)
        .updatedAt(row.getLocalDateTime("updated_at") != null
            ? row.getLocalDateTime("updated_at").toString() : null)
        .build();
  }
}
