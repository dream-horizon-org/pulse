package org.dreamhorizon.pulseserver.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.dreamhorizon.pulseserver.resources.alert.enums.AlertState;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertFiltersResponseDto;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertMapperTest {

  @SuppressWarnings("unchecked")
  private RowSet<Row> createMockRowSet(List<Row> rows) {
    RowSet<Row> rowSet = mock(RowSet.class);
    
    // RowSet is Iterable, so mock forEach behavior for the for-each loop
    doAnswer(invocation -> {
      Consumer<Row> consumer = invocation.getArgument(0);
      for (Row row : rows) {
        consumer.accept(row);
      }
      return null;
    }).when(rowSet).forEach(org.mockito.ArgumentMatchers.any());
    
    // Also mock the iterator for direct iteration
    RowIterator<Row> rowIterator = mock(RowIterator.class);
    int[] index = {0};
    when(rowIterator.hasNext()).thenAnswer(inv -> index[0] < rows.size());
    when(rowIterator.next()).thenAnswer(inv -> rows.get(index[0]++));
    when(rowSet.iterator()).thenReturn(rowIterator);
    
    return rowSet;
  }

  private Row createMockRow(String createdBy, String updatedBy, String currentState) {
    Row row = mock(Row.class);
    when(row.getString("created_by")).thenReturn(createdBy);
    when(row.getString("updated_by")).thenReturn(updatedBy);
    when(row.getString("current_state")).thenReturn(currentState);
    return row;
  }

  @Nested
  class TestMapRowSetToAlertFilters {

    @Test
    void shouldReturnEmptyListsForEmptyRowSet() {
      RowSet<Row> rowSet = createMockRowSet(Collections.emptyList());

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertTrue(result.getCreatedBy().isEmpty());
      assertTrue(result.getUpdatedBy().isEmpty());
      assertTrue(result.getCurrentState().isEmpty());
    }

    @Test
    void shouldMapSingleRow() {
      Row row = createMockRow("user1", "user2", "NORMAL");
      RowSet<Row> rowSet = createMockRowSet(List.of(row));

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertEquals(1, result.getCreatedBy().size());
      assertEquals("user1", result.getCreatedBy().get(0));
      assertEquals(1, result.getUpdatedBy().size());
      assertEquals("user2", result.getUpdatedBy().get(0));
      assertEquals(1, result.getCurrentState().size());
      assertEquals(AlertState.NORMAL, result.getCurrentState().get(0));
    }

    @Test
    void shouldMapMultipleRowsWithUniqueValues() {
      Row row1 = createMockRow("user1", "admin1", "NORMAL");
      Row row2 = createMockRow("user2", "admin2", "FIRING");
      RowSet<Row> rowSet = createMockRowSet(List.of(row1, row2));

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertEquals(2, result.getCreatedBy().size());
      assertTrue(result.getCreatedBy().contains("user1"));
      assertTrue(result.getCreatedBy().contains("user2"));
      assertEquals(2, result.getUpdatedBy().size());
      assertTrue(result.getUpdatedBy().contains("admin1"));
      assertTrue(result.getUpdatedBy().contains("admin2"));
      assertEquals(2, result.getCurrentState().size());
      assertTrue(result.getCurrentState().contains(AlertState.NORMAL));
      assertTrue(result.getCurrentState().contains(AlertState.FIRING));
    }

    @Test
    void shouldNotAddDuplicateCreatedBy() {
      Row row1 = createMockRow("user1", "admin1", "NORMAL");
      Row row2 = createMockRow("user1", "admin2", "FIRING");
      RowSet<Row> rowSet = createMockRowSet(List.of(row1, row2));

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertEquals(1, result.getCreatedBy().size());
      assertEquals("user1", result.getCreatedBy().get(0));
    }

    @Test
    void shouldNotAddDuplicateUpdatedBy() {
      Row row1 = createMockRow("user1", "admin1", "NORMAL");
      Row row2 = createMockRow("user2", "admin1", "FIRING");
      RowSet<Row> rowSet = createMockRowSet(List.of(row1, row2));

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertEquals(1, result.getUpdatedBy().size());
      assertEquals("admin1", result.getUpdatedBy().get(0));
    }

    @Test
    void shouldNotAddDuplicateCurrentState() {
      Row row1 = createMockRow("user1", "admin1", "NORMAL");
      Row row2 = createMockRow("user2", "admin2", "NORMAL");
      RowSet<Row> rowSet = createMockRowSet(List.of(row1, row2));

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertEquals(1, result.getCurrentState().size());
      assertEquals(AlertState.NORMAL, result.getCurrentState().get(0));
    }

    @Test
    void shouldIgnoreNullCreatedBy() {
      Row row = createMockRow(null, "admin", "NORMAL");
      RowSet<Row> rowSet = createMockRowSet(List.of(row));

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertTrue(result.getCreatedBy().isEmpty());
    }

    @Test
    void shouldIgnoreNullUpdatedBy() {
      Row row = createMockRow("user", null, "NORMAL");
      RowSet<Row> rowSet = createMockRowSet(List.of(row));

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertTrue(result.getUpdatedBy().isEmpty());
    }

    @Test
    void shouldIgnoreNullCurrentState() {
      Row row = createMockRow("user", "admin", null);
      RowSet<Row> rowSet = createMockRowSet(List.of(row));

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertTrue(result.getCurrentState().isEmpty());
    }

    @Test
    void shouldIgnoreEmptyCreatedBy() {
      Row row = createMockRow("", "admin", "NORMAL");
      RowSet<Row> rowSet = createMockRowSet(List.of(row));

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertTrue(result.getCreatedBy().isEmpty());
    }

    @Test
    void shouldIgnoreEmptyUpdatedBy() {
      Row row = createMockRow("user", "", "NORMAL");
      RowSet<Row> rowSet = createMockRowSet(List.of(row));

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertTrue(result.getUpdatedBy().isEmpty());
    }

    @Test
    void shouldIgnoreEmptyCurrentState() {
      Row row = createMockRow("user", "admin", "");
      RowSet<Row> rowSet = createMockRowSet(List.of(row));

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertTrue(result.getCurrentState().isEmpty());
    }

    @Test
    void shouldMapAllAlertStates() {
      Row row1 = createMockRow("user1", "admin1", "NORMAL");
      Row row2 = createMockRow("user2", "admin2", "FIRING");
      Row row3 = createMockRow("user3", "admin3", "SILENCED");
      Row row4 = createMockRow("user4", "admin4", "NO_DATA");
      Row row5 = createMockRow("user5", "admin5", "ERRORED");
      Row row6 = createMockRow("user6", "admin6", "QUERY_FAILED");
      RowSet<Row> rowSet = createMockRowSet(List.of(row1, row2, row3, row4, row5, row6));

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertEquals(6, result.getCurrentState().size());
      assertTrue(result.getCurrentState().contains(AlertState.NORMAL));
      assertTrue(result.getCurrentState().contains(AlertState.FIRING));
      assertTrue(result.getCurrentState().contains(AlertState.SILENCED));
      assertTrue(result.getCurrentState().contains(AlertState.NO_DATA));
      assertTrue(result.getCurrentState().contains(AlertState.ERRORED));
      assertTrue(result.getCurrentState().contains(AlertState.QUERY_FAILED));
    }

    @Test
    void shouldHandleMixedNullAndValidValues() {
      Row row1 = createMockRow("user1", null, "NORMAL");
      Row row2 = createMockRow(null, "admin2", "FIRING");
      Row row3 = createMockRow("user3", "admin3", null);
      RowSet<Row> rowSet = createMockRowSet(List.of(row1, row2, row3));

      AlertFiltersResponseDto result = AlertMapper.mapRowSetToAlertFilters(rowSet);

      assertNotNull(result);
      assertEquals(2, result.getCreatedBy().size());
      assertTrue(result.getCreatedBy().contains("user1"));
      assertTrue(result.getCreatedBy().contains("user3"));
      assertEquals(2, result.getUpdatedBy().size());
      assertTrue(result.getUpdatedBy().contains("admin2"));
      assertTrue(result.getUpdatedBy().contains("admin3"));
      assertEquals(2, result.getCurrentState().size());
    }
  }
}

