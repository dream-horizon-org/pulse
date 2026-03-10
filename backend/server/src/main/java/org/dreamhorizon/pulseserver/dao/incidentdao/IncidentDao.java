package org.dreamhorizon.pulseserver.dao.incidentdao;

import static org.dreamhorizon.pulseserver.dao.incidentdao.IncidentQueries.*;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.incidentdao.models.IncidentRow;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentSeverity;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentStatus;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class IncidentDao {

  private final MysqlClient mysqlClient;

  public Single<IncidentRow> insertIncident(IncidentRow incident) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(INSERT_INCIDENT)
        .rxExecute(Tuple.of(
            incident.getTitle(),
            incident.getDescription(),
            incident.getSeverity().name(),
            incident.getReporterName(),
            incident.getReporterEmail(),
            incident.getOrgIdentifier()
        ))
        .flatMap(result -> {
          long generatedId = Long.parseLong(result.property(MySQLClient.LAST_INSERTED_ID).toString());
          log.info("Inserted incident with id={}", generatedId);
          return getIncidentById(generatedId);
        })
        .doOnError(error -> log.error("Failed to insert incident: title={}", incident.getTitle(), error));
  }

  public Single<IncidentRow> getIncidentById(long id) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_INCIDENT_BY_ID)
        .rxExecute(Tuple.of(id))
        .map(rowSet -> {
          if (rowSet.size() == 0) {
            throw new RuntimeException("Incident not found with id: " + id);
          }
          return mapRowToIncident(rowSet.iterator().next());
        })
        .doOnError(error -> log.error("Failed to fetch incident: id={}", id, error));
  }

  public Single<IncidentRow> acknowledgeIncident(long id) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(ACKNOWLEDGE_INCIDENT)
        .rxExecute(Tuple.of(id))
        .flatMap(result -> {
          if (result.rowCount() == 0) {
            return Single.error(new RuntimeException(
                "Cannot acknowledge incident " + id + ": not in OPEN state or not found"));
          }
          log.info("Acknowledged incident id={}", id);
          return getIncidentById(id);
        })
        .doOnError(error -> log.error("Failed to acknowledge incident: id={}", id, error));
  }

  public Single<IncidentRow> recoverIncident(long id) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(RECOVER_INCIDENT)
        .rxExecute(Tuple.of(id))
        .flatMap(result -> {
          if (result.rowCount() == 0) {
            return Single.error(new RuntimeException(
                "Cannot recover incident " + id + ": not in ACKNOWLEDGED state or not found"));
          }
          log.info("Recovered incident id={}", id);
          return getIncidentById(id);
        })
        .doOnError(error -> log.error("Failed to recover incident: id={}", id, error));
  }

  public Single<IncidentRow> closeIncident(long id) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(CLOSE_INCIDENT)
        .rxExecute(Tuple.of(id))
        .flatMap(result -> {
          if (result.rowCount() == 0) {
            return Single.error(new RuntimeException(
                "Cannot close incident " + id + ": not in RECOVERED state or not found"));
          }
          log.info("Closed incident id={}", id);
          return getIncidentById(id);
        })
        .doOnError(error -> log.error("Failed to close incident: id={}", id, error));
  }

  private IncidentRow mapRowToIncident(Row row) {
    return IncidentRow.builder()
        .id(row.getLong("id"))
        .title(row.getString("title"))
        .description(row.getString("description"))
        .severity(IncidentSeverity.valueOf(row.getString("severity")))
        .reporterName(row.getString("reporter_name"))
        .reporterEmail(row.getString("reporter_email"))
        .orgIdentifier(row.getString("org_identifier"))
        .status(IncidentStatus.valueOf(row.getString("status")))
        .createdAt(row.getLocalDateTime("created_at") != null
            ? row.getLocalDateTime("created_at").toString() : null)
        .updatedAt(row.getLocalDateTime("updated_at") != null
            ? row.getLocalDateTime("updated_at").toString() : null)
        .acknowledgedAt(row.getLocalDateTime("acknowledged_at") != null
            ? row.getLocalDateTime("acknowledged_at").toString() : null)
        .recoveredAt(row.getLocalDateTime("recovered_at") != null
            ? row.getLocalDateTime("recovered_at").toString() : null)
        .closedAt(row.getLocalDateTime("closed_at") != null
            ? row.getLocalDateTime("closed_at").toString() : null)
        .build();
  }
}
