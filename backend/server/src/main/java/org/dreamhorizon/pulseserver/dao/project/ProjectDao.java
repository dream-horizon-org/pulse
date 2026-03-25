package org.dreamhorizon.pulseserver.dao.project;

import static org.dreamhorizon.pulseserver.dao.project.ProjectQueries.CHECK_PROJECT_EXISTS;
import static org.dreamhorizon.pulseserver.dao.project.ProjectQueries.DEACTIVATE_PROJECT;
import static org.dreamhorizon.pulseserver.dao.project.ProjectQueries.GET_ACTIVE_PROJECT_COUNT;
import static org.dreamhorizon.pulseserver.dao.project.ProjectQueries.GET_PROJECTS_BY_TENANT_ID;
import static org.dreamhorizon.pulseserver.dao.project.ProjectQueries.GET_PROJECT_BY_PROJECT_ID;
import static org.dreamhorizon.pulseserver.dao.project.ProjectQueries.INSERT_PROJECT;
import static org.dreamhorizon.pulseserver.dao.project.ProjectQueries.UPDATE_PROJECT;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.project.models.Project;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectDao {
  private final MysqlClient mysqlClient;

  public Single<Project> createProject(SqlConnection conn, Project project) {
    return conn.preparedQuery(INSERT_PROJECT)
        .rxExecute(
            Tuple.of(
                project.getProjectId(),
                project.getTenantId(),
                project.getName(),
                project.getDescription(),
                true,
                project.getCreatedBy()))
        .map(result -> mapToCreatedProject(result, project))
        .doOnError(error -> log.error("Failed to create project for tenant: {}", project.getTenantId(), error));
  }

  private Project mapToCreatedProject(io.vertx.rxjava3.sqlclient.RowSet<Row> result, Project project) {
    long generatedId = Long.parseLong(result.property(io.vertx.rxjava3.mysqlclient.MySQLClient.LAST_INSERTED_ID).toString());
    log.info("Created project: {} (id: {}) for tenant: {}", project.getProjectId(), generatedId, project.getTenantId());
    return Project.builder()
        .id((int) generatedId)
        .projectId(project.getProjectId())
        .tenantId(project.getTenantId())
        .name(project.getName())
        .description(project.getDescription())
        .isActive(true)
        .createdBy(project.getCreatedBy())
        .build();
  }

  public Maybe<Project> getProjectByProjectId(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_PROJECT_BY_PROJECT_ID)
        .rxExecute(Tuple.of(projectId))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToProject(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch project: {}", projectId, error));
  }


  public Flowable<Project> getProjectsByTenantId(String tenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_PROJECTS_BY_TENANT_ID)
        .rxExecute(Tuple.of(tenantId))
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToProject((Row) row)))
        .doOnError(error -> log.error("Failed to fetch projects for tenant: {}", tenantId, error));
  }


  public Single<Project> updateProject(Project project) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(UPDATE_PROJECT)
        .rxExecute(Tuple.of(project.getName(), project.getDescription(), project.getProjectId()))
        .flatMap(result -> {
          if (result.rowCount() == 0) {
            return Single.error(new RuntimeException("Project not found: " + project.getProjectId()));
          }
          log.info("Updated project: {}", project.getProjectId());
          return Single.just(project);
        })
        .doOnError(error -> log.error("Failed to update project: {}", project.getProjectId(), error));
  }

  public Completable deactivateProject(String projectId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(DEACTIVATE_PROJECT)
        .rxExecute(Tuple.of(projectId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            log.warn("No project found to deactivate: {}", projectId);
          } else {
            log.info("Deactivated project: {}", projectId);
          }
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to deactivate project: {}", projectId, error));
  }


  public Single<Boolean> projectExists(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(CHECK_PROJECT_EXISTS)
        .rxExecute(Tuple.of(projectId))
        .map(rowSet -> {
          Row row = rowSet.iterator().next();
          return row.getLong("count") > 0;
        })
        .doOnError(error -> log.error("Failed to check project existence: {}", projectId, error));
  }

  private Project mapRowToProject(Row row) {
    return Project.builder()
        .id(row.getInteger("id"))
        .projectId(row.getString("project_id"))
        .tenantId(row.getString("tenant_id"))
        .name(row.getString("name"))
        .description(row.getString("description"))
        .isActive(row.getBoolean("is_active"))
        .createdBy(row.getString("created_by"))
        .createdAt(row.getLocalDateTime("created_at") != null ? row.getLocalDateTime("created_at").toString() : null)
        .updatedAt(row.getLocalDateTime("updated_at") != null ? row.getLocalDateTime("updated_at").toString() : null)
        .build();
  }

  public Single<Integer> getActiveProjectCount(String tenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_ACTIVE_PROJECT_COUNT)
        .rxExecute(Tuple.of(tenantId))
        .map(rowSet -> {
          Row row = rowSet.iterator().next();
          int count = row.getLong("count").intValue();
          log.debug("Active project count for tenant {}: {}", tenantId, count);
          return count;
        })
        .doOnError(error -> log.error("Failed to get project count for tenant: {}", tenantId, error));
  }
}

