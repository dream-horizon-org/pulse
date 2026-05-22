package org.dreamhorizon.pulseserver.errorgrouping.dao;

import static org.dreamhorizon.pulseserver.errorgrouping.dao.GroupingRuleQueries.GET_RULES_FOR_PROJECT;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;

/**
 * Vert.x MySQL DAO over the {@code grouping_rule} table. Returns the raw rows
 * for a project — partitioning / regex compilation / bundle assembly live in
 * the cache layer ({@code GroupingRuleCache}) so this class stays focused on
 * I/O.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class GroupingRuleDao {

  private final MysqlClient mysqlClient;

  /**
   * Load every enabled rule for a project. The DB-side {@code ORDER BY rule_kind,
   * position ASC} guarantees the cache loader sees rules grouped by kind in
   * priority order.
   *
   * <p>On a downstream error the failure is logged and propagated — the caller
   * (cache loader) decides whether to surface or fall back to
   * {@code GroupingRules.empty()}.</p>
   */
  public Single<List<GroupingRuleRow>> loadRulesForProject(String projectId) {
    return Single.defer(() -> {
      MySQLPool pool = mysqlClient.getReaderPool();
      return pool.preparedQuery(GET_RULES_FOR_PROJECT)
          .rxExecute(Tuple.of(projectId))
          .map(rowSet -> {
            if (rowSet.size() == 0) {
              return Collections.<GroupingRuleRow>emptyList();
            }
            List<GroupingRuleRow> rows = new ArrayList<>(rowSet.size());
            for (Row row : rowSet) {
              rows.add(toRow(row));
            }
            return rows;
          })
          .doOnError(error ->
              log.error("Failed to load grouping rules: projectId={}", projectId, error));
    });
  }

  private GroupingRuleRow toRow(Row row) {
    return GroupingRuleRow.builder()
        .id(row.getLong("id"))
        .projectId(row.getString("project_id"))
        .ruleKind(row.getString("rule_kind"))
        .pattern(row.getString("pattern"))
        .replacement(row.getString("replacement"))
        .position(row.getInteger("position"))
        .enabled(row.getBoolean("enabled"))
        .build();
  }
}
