package org.dreamhorizon.pulseserver.dao.productAnalysis.funneljourneytag;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;

@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelJourneyTagDao {

  private final MysqlClient mysqlClient;

  /** All distinct tags used on any funnel or journey in the project. */
  public Single<List<String>> listDistinctTagsForProject(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool
        .preparedQuery(FunnelJourneyTagQueries.SELECT_DISTINCT_TAGS_BY_PROJECT)
        .rxExecute(Tuple.of(projectId))
        .map(FunnelJourneyTagDao::rowsToTagList);
  }

  public Single<List<String>> listTagsForEntity(
      String projectId, FunnelJourneyTagEntityType entityType, long entityId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool
        .preparedQuery(FunnelJourneyTagQueries.SELECT_TAGS_FOR_ENTITY)
        .rxExecute(Tuple.of(projectId, entityType.name(), entityId))
        .map(FunnelJourneyTagDao::rowsToTagList);
  }

  /**
   * Returns a map from entity id to ordered tag list (possibly empty per id). Missing ids have no
   * entry (caller may treat as empty).
   */
  public Single<Map<Long, List<String>>> listTagsForEntities(
      String projectId, FunnelJourneyTagEntityType entityType, List<Long> entityIds) {
    if (entityIds == null || entityIds.isEmpty()) {
      return Single.just(Map.of());
    }
    MySQLPool pool = mysqlClient.getReaderPool();
    String sql = FunnelJourneyTagQueries.buildSelectTagsForEntitiesIn(entityIds.size());
    List<Object> params = new ArrayList<>(2 + entityIds.size());
    params.add(projectId);
    params.add(entityType.name());
    params.addAll(entityIds);
    return pool
        .preparedQuery(sql)
        .rxExecute(Tuple.wrap(params.toArray()))
        .map(FunnelJourneyTagDao::rowsToEntityTagMap);
  }

  public Completable deleteAllForEntity(
      String projectId, FunnelJourneyTagEntityType entityType, long entityId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool
        .preparedQuery(FunnelJourneyTagQueries.DELETE_ALL_FOR_ENTITY)
        .rxExecute(Tuple.of(projectId, entityType.name(), entityId))
        .ignoreElement();
  }

  /**
   * Replaces all tags for the entity with {@code tags} (delete then insert). Runs in a single
   * transaction.
   */
  public Completable replaceTags(
      String projectId,
      FunnelJourneyTagEntityType entityType,
      long entityId,
      List<String> tags) {
    MySQLPool pool = mysqlClient.getWriterPool();
    Tuple deleteTuple = Tuple.of(projectId, entityType.name(), entityId);
    return pool
        .rxGetConnection()
        .flatMap(
            conn ->
                conn.rxBegin()
                    .flatMap(
                        tx ->
                            conn.preparedQuery(FunnelJourneyTagQueries.DELETE_ALL_FOR_ENTITY)
                                .rxExecute(deleteTuple)
                                .flatMap(
                                    ignored -> {
                                      if (tags.isEmpty()) {
                                        return tx.rxCommit().toSingleDefault(true);
                                      }
                                      return conn
                                          .preparedQuery(
                                              FunnelJourneyTagQueries.buildBatchInsert(tags.size()))
                                          .rxExecute(
                                              FunnelJourneyTagQueries.batchInsertTuple(
                                                  projectId, entityType, entityId, tags))
                                          .flatMap(ins -> tx.rxCommit().toSingleDefault(true));
                                    })
                                .onErrorResumeNext(
                                    err ->
                                        tx.rxRollback()
                                            .onErrorComplete()
                                            .andThen(Single.error(err))))
                    .doFinally(conn::close))
        .ignoreElement();
  }

  private static List<String> rowsToTagList(RowSet<Row> rows) {
    List<String> out = new ArrayList<>();
    for (Row row : rows) {
      String t = row.getString("tag");
      if (t != null) {
        out.add(t);
      }
    }
    return out;
  }

  private static Map<Long, List<String>> rowsToEntityTagMap(RowSet<Row> rows) {
    Map<Long, List<String>> map = new HashMap<>();
    for (Row row : rows) {
      Long eid = row.getLong("entity_id");
      String t = row.getString("tag");
      if (eid == null || t == null) {
        continue;
      }
      map.computeIfAbsent(eid, k -> new ArrayList<>()).add(t);
    }
    return map;
  }
}
