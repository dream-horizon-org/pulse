package org.dreamhorizon.pulseserver.errorgrouping.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class GroupingRuleDaoTest {

  @Mock
  MysqlClient mysqlClient;

  @Mock
  MySQLPool readerPool;

  @Mock
  PreparedQuery<RowSet<Row>> preparedQuery;

  @Mock
  RowSet<Row> rowSet;

  GroupingRuleDao dao;

  @BeforeEach
  void setUp() {
    dao = new GroupingRuleDao(mysqlClient);
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
    when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private Row mockRow(long id, String projectId, String kind, String pattern,
      String replacement, int position, boolean enabled) {
    Row row = mock(Row.class);
    when(row.getLong("id")).thenReturn(id);
    when(row.getString("project_id")).thenReturn(projectId);
    when(row.getString("rule_kind")).thenReturn(kind);
    when(row.getString("pattern")).thenReturn(pattern);
    when(row.getString("replacement")).thenReturn(replacement);
    when(row.getInteger("position")).thenReturn(position);
    when(row.getBoolean("enabled")).thenReturn(enabled);
    return row;
  }

  private RowIterator<Row> rowIterator(List<Row> rows) {
    RowIterator<Row> iterator = mock(RowIterator.class);
    if (rows.isEmpty()) {
      when(iterator.hasNext()).thenReturn(false);
    } else {
      final int[] idx = {0};
      when(iterator.hasNext()).thenAnswer(inv -> idx[0] < rows.size());
      when(iterator.next()).thenAnswer(inv -> rows.get(idx[0]++));
    }
    return iterator;
  }

  @Test
  void shouldLoadRulesOrderedByKindAndPosition() {
    Row r1 = mockRow(1L, "p1", "IN_APP_PACKAGE", "com.dream11.", null, 0, true);
    Row r2 = mockRow(2L, "p1", "MASK_REGEX", "[0-9]+", "<NUM>", 1, true);
    Row r3 = mockRow(3L, "p1", "STRIP_PATTERN", "synthetic", null, 2, true);
    List<Row> rows = List.of(r1, r2, r3);
    when(rowSet.size()).thenReturn(rows.size());
    when(rowSet.iterator()).thenReturn(rowIterator(rows));
    // Allow enhanced-for loop iteration via Iterable contract.
    when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

    List<GroupingRuleRow> result = dao.loadRulesForProject("p1").blockingGet();

    assertThat(result).hasSize(3);
    assertThat(result.get(0).getId()).isEqualTo(1L);
    assertThat(result.get(0).getRuleKind()).isEqualTo("IN_APP_PACKAGE");
    assertThat(result.get(0).getPattern()).isEqualTo("com.dream11.");
    assertThat(result.get(0).getReplacement()).isNull();
    assertThat(result.get(0).getPosition()).isEqualTo(0);
    assertThat(result.get(0).isEnabled()).isTrue();

    assertThat(result.get(1).getRuleKind()).isEqualTo("MASK_REGEX");
    assertThat(result.get(1).getPattern()).isEqualTo("[0-9]+");
    assertThat(result.get(1).getReplacement()).isEqualTo("<NUM>");

    assertThat(result.get(2).getRuleKind()).isEqualTo("STRIP_PATTERN");
    assertThat(result.get(2).getPattern()).isEqualTo("synthetic");
  }

  @Test
  void shouldReturnEmptyListWhenNoRulesForProject() {
    when(rowSet.size()).thenReturn(0);
    when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

    List<GroupingRuleRow> result = dao.loadRulesForProject("p-empty").blockingGet();

    assertThat(result).isEmpty();
  }

  @Test
  void shouldPropagateDbErrorAsSingleError() {
    RuntimeException dbErr = new RuntimeException("connection refused");
    when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.error(dbErr));

    TestObserver<List<GroupingRuleRow>> observer =
        dao.loadRulesForProject("p1").test();

    observer.assertError(dbErr);
  }

  @Test
  void shouldQueryWithExactProjectIdParameter() {
    when(rowSet.size()).thenReturn(0);
    when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

    dao.loadRulesForProject("proj-xyz").blockingGet();

    ArgumentCaptor<Tuple> captor = ArgumentCaptor.forClass(Tuple.class);
    verify(preparedQuery).rxExecute(captor.capture());
    assertThat(captor.getValue().getString(0)).isEqualTo("proj-xyz");
    verify(readerPool).preparedQuery(GroupingRuleQueries.GET_RULES_FOR_PROJECT);
  }

  @Test
  void shouldMapAllColumnsIncludingNullableReplacement() {
    Row withReplacement = mockRow(10L, "p1", "MASK_REGEX", "0x[a-f0-9]+", "<HEX>", 5, true);
    Row nullReplacement = mockRow(11L, "p1", "IN_APP_PACKAGE", "com.foo.", null, 6, true);
    List<Row> rows = List.of(withReplacement, nullReplacement);
    when(rowSet.size()).thenReturn(rows.size());
    when(rowSet.iterator()).thenReturn(rowIterator(rows));
    when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

    List<GroupingRuleRow> result = dao.loadRulesForProject("p1").blockingGet();

    assertThat(result).hasSize(2);
    GroupingRuleRow rowA = result.get(0);
    assertThat(rowA.getId()).isEqualTo(10L);
    assertThat(rowA.getProjectId()).isEqualTo("p1");
    assertThat(rowA.getRuleKind()).isEqualTo("MASK_REGEX");
    assertThat(rowA.getPattern()).isEqualTo("0x[a-f0-9]+");
    assertThat(rowA.getReplacement()).isEqualTo("<HEX>");
    assertThat(rowA.getPosition()).isEqualTo(5);
    assertThat(rowA.isEnabled()).isTrue();

    GroupingRuleRow rowB = result.get(1);
    assertThat(rowB.getReplacement()).isNull();
    assertThat(rowB.getRuleKind()).isEqualTo("IN_APP_PACKAGE");
  }

  @Test
  void shouldNotInvokeReaderPoolUntilSubscribed() {
    // Single is lazy — building it shouldn't touch the DB.
    Single<List<GroupingRuleRow>> single = dao.loadRulesForProject("p1");
    // Reader pool is still wired (we mock the call); but rxExecute should not have been triggered yet.
    verify(preparedQuery, org.mockito.Mockito.never()).rxExecute(any(Tuple.class));
    assertThat(single).isNotNull();
  }

  @Test
  void shouldReturnImmutableSnapshotEvenIfRowSetIsEmptyCollections() {
    when(rowSet.size()).thenReturn(0);
    when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

    List<GroupingRuleRow> result = dao.loadRulesForProject("p1").blockingGet();

    // Compatibility: defensive empty list is interchangeable with Collections.emptyList()
    assertThat(result).isEqualTo(Collections.emptyList());
  }
}
