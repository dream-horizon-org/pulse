package org.dreamhorizon.pulseserver.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLException;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Query;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Transaction;
import io.vertx.rxjava3.sqlclient.Tuple;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.resources.alert.enums.AlertState;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertFiltersResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertNotificationChannelResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertScopeItemDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertSeverityResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertTagsResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.MetricItemDto;
import org.dreamhorizon.pulseserver.resources.alert.models.ScopeEvaluationHistoryDto;
import org.dreamhorizon.pulseserver.service.alert.core.models.Alert;
import org.dreamhorizon.pulseserver.service.alert.core.models.AlertCondition;
import org.dreamhorizon.pulseserver.service.alert.core.models.AlertScope;
import org.dreamhorizon.pulseserver.service.alert.core.models.CreateAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.DeleteSnoozeRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.GetAlertsResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.GetAllAlertsResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.Metric;
import org.dreamhorizon.pulseserver.service.alert.core.models.MetricOperator;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.UpdateAlertRequest;
import org.dreamhorizon.pulseserver.util.DateTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@SuppressWarnings("unchecked")
class AlertsDaoTest {

  @Mock
  MysqlClient d11MysqlClient;

  @Mock
  DateTimeUtil dateTimeUtil;

  @Mock
  MySQLPool writerPool;

  @Mock
  PreparedQuery<RowSet<Row>> preparedQuery;

  @Mock
  Query<RowSet<Row>> query;

  @Mock
  RowSet<Row> rowSet;

  @Mock
  Row row;

  @Mock
  SqlConnection sqlConnection;

  @Mock
  Transaction transaction;

  AlertsDao alertsDao;

  @BeforeEach
  public void setup() {
    alertsDao = new AlertsDao(d11MysqlClient, dateTimeUtil);
  }

  private void setupWriterPool() {
    when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
  }

  private void setupPreparedQuery() {
    setupWriterPool();
    when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private RowIterator<Row> createMockRowIterator(List<Row> rows) {
    RowIterator<Row> iterator = mock(RowIterator.class);
    if (rows.isEmpty()) {
      when(iterator.hasNext()).thenReturn(false);
    } else {
      final int[] index = {0};
      when(iterator.hasNext()).thenAnswer(invocation -> index[0] < rows.size());
      when(iterator.next()).thenAnswer(invocation -> {
        if (index[0] < rows.size()) {
          return rows.get(index[0]++);
        }
        throw new java.util.NoSuchElementException();
      });
    }
    return iterator;
  }

  private void setupRowSetMock(RowSet<Row> rowSet, List<Row> rows) {
    when(rowSet.size()).thenReturn(rows.size());
    // Create iterator for iterator() calls
    RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>(rows));
    when(rowSet.iterator()).thenReturn(iterator);
    // Mock forEach by delegating to the actual list
    org.mockito.Mockito.doAnswer(invocation -> {
      java.util.function.Consumer<Row> consumer = invocation.getArgument(0);
      rows.forEach(consumer);
      return null;
    }).when(rowSet).forEach(any());
  }

  private Row createMockAlertRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getInteger("alert_id")).thenReturn(1);
    when(mockRow.getString("name")).thenReturn("Test Alert");
    when(mockRow.getString("description")).thenReturn("Test Description");
    when(mockRow.getString("scope")).thenReturn("Interaction");
    when(mockRow.getString("dimension_filter")).thenReturn("{}");
    when(mockRow.getString("condition_expression")).thenReturn("A > 100");
    when(mockRow.getInteger("evaluation_period")).thenReturn(300);
    when(mockRow.getInteger("evaluation_interval")).thenReturn(60);
    when(mockRow.getInteger("severity_id")).thenReturn(1);
    when(mockRow.getInteger("notification_channel_id")).thenReturn(1);
    when(mockRow.getString("notification_webhook_url")).thenReturn("https://webhook.url");
    when(mockRow.getString("created_by")).thenReturn("user1");
    when(mockRow.getString("updated_by")).thenReturn("user2");
    when(mockRow.getLocalDateTime("alert_created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("alert_updated_at")).thenReturn(now);
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getLocalDateTime("last_snoozed_at")).thenReturn(null);
    when(mockRow.getLocalDateTime("snoozed_from")).thenReturn(null);
    when(mockRow.getLocalDateTime("snoozed_until")).thenReturn(null);
    return mockRow;
  }

  private Row createMockScopeRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    JsonArray conditions = new JsonArray()
        .add(new JsonObject()
            .put("alias", "A")
            .put("metric", "APDEX")
            .put("metric_operator", "GREATER_THAN")
            .put("threshold", 0.5));
    when(mockRow.getInteger("id")).thenReturn(1);
    when(mockRow.getInteger("alert_id")).thenReturn(1);
    when(mockRow.getString("name")).thenReturn("scope1");
    when(mockRow.getValue("conditions")).thenReturn(conditions);
    when(mockRow.getString("state")).thenReturn("NORMAL");
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("updated_at")).thenReturn(now);
    return mockRow;
  }

  @Nested
  class TestSnoozeAlerts {

    @Test
    void shouldSnoozeSuccessfully() {
      LocalDateTime now = LocalDateTime.of(2024, 1, 1, 0, 0);
      LocalDateTime snoozeFrom = now.plusMinutes(1);
      LocalDateTime snoozeUntil = now.plusHours(1);
      SnoozeAlertRequest request = SnoozeAlertRequest.builder()
          .alertId(42)
          .snoozeFrom(snoozeFrom)
          .snoozeUntil(snoozeUntil)
          .updatedBy("test_user")
          .build();

      when(dateTimeUtil.getLocalDateTime(ZoneOffset.UTC)).thenReturn(now);
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      when(preparedQuery.rxExecute(tupleCaptor.capture())).thenReturn(Single.just(rowSet));

      EmptyResponse resp = alertsDao.snoozeAlert(request).blockingGet();
      assertEquals(EmptyResponse.emptyResponse, resp);

      Tuple tuple = tupleCaptor.getValue();
      assertEquals(now, tuple.getLocalDateTime(0));
      assertEquals(snoozeFrom, tuple.getLocalDateTime(1));
      assertEquals(snoozeUntil, tuple.getLocalDateTime(2));
      assertEquals("test_user", tuple.getString(3));
      assertEquals(42, tuple.getInteger(4));
    }

    @Test
    void shouldThrowExceptionWhenDatabaseCallThrowsException() {
      LocalDateTime now = LocalDateTime.of(2024, 1, 1, 0, 0);
      LocalDateTime snoozeFrom = now.plusMinutes(1);
      LocalDateTime snoozeUntil = now.plusHours(1);
      SnoozeAlertRequest request = SnoozeAlertRequest.builder()
          .alertId(42)
          .snoozeFrom(snoozeFrom)
          .snoozeUntil(snoozeUntil)
          .updatedBy("test_user")
          .build();

      when(dateTimeUtil.getLocalDateTime(ZoneOffset.UTC)).thenReturn(now);
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 400, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class, () -> alertsDao.snoozeAlert(request).blockingGet());
      assertEquals("{errorMessage=DB Error, errorCode=400, sqlState=SQLSTATE}", ex.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenNoRowsAreUpdated() {
      LocalDateTime now = LocalDateTime.of(2024, 1, 1, 0, 0);
      LocalDateTime snoozeFrom = now.plusMinutes(1);
      LocalDateTime snoozeUntil = now.plusHours(1);
      SnoozeAlertRequest request = SnoozeAlertRequest.builder()
          .alertId(42)
          .snoozeFrom(snoozeFrom)
          .snoozeUntil(snoozeUntil)
          .updatedBy("test_user")
          .build();

      when(dateTimeUtil.getLocalDateTime(ZoneOffset.UTC)).thenReturn(now);
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Exception ex = assertThrows(WebApplicationException.class, () -> alertsDao.snoozeAlert(request).blockingGet());
      assertEquals("Error while updating alert in db", ex.getMessage());
    }
  }

  @Nested
  class TestDeleteSnooze {

    @Test
    void shouldDeleteSnoozeSuccessfully() {
      DeleteSnoozeRequest request = DeleteSnoozeRequest.builder()
          .alertId(99)
          .updatedBy("resume_user")
          .build();

      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      when(preparedQuery.rxExecute(tupleCaptor.capture())).thenReturn(Single.just(rowSet));

      EmptyResponse resp = alertsDao.deleteSnooze(request).blockingGet();
      assertEquals(EmptyResponse.emptyResponse, resp);

      Tuple tuple = tupleCaptor.getValue();
      assertEquals("resume_user", tuple.getString(0));
      assertEquals(99, tuple.getInteger(1));
    }

    @Test
    void shouldThrowExceptionWhenDatabaseCallThrowsException() {
      DeleteSnoozeRequest request = DeleteSnoozeRequest.builder()
          .alertId(99)
          .updatedBy("resume_user")
          .build();

      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 400, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class, () -> alertsDao.deleteSnooze(request).blockingGet());
      assertEquals("{errorMessage=DB Error, errorCode=400, sqlState=SQLSTATE}", ex.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenNoRowsAreUpdated() {
      DeleteSnoozeRequest request = DeleteSnoozeRequest.builder()
          .alertId(99)
          .updatedBy("resume_user")
          .build();

      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Exception ex = assertThrows(WebApplicationException.class, () -> alertsDao.deleteSnooze(request).blockingGet());
      assertEquals("Error while deleting snooze from alert in db", ex.getMessage());
    }
  }

  @Nested
  class TestDeleteAlert {

    @Test
    void shouldDeleteAlertSuccessfully() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.deleteAlert(1).blockingGet();
      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenNoRowsDeleted() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.deleteAlert(1).blockingGet();
      assertFalse(result);
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class, () -> alertsDao.deleteAlert(1).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetAlertDetails {

    @Test
    void shouldGetAlertDetailsSuccessfully() {
      setupWriterPool();
      Row alertRow = createMockAlertRow();
      Row scopeRow = createMockScopeRow();

      RowSet<Row> alertRowSet = mock(RowSet.class);
      RowSet<Row> scopeRowSet = mock(RowSet.class);

      // Create mock iterators BEFORE using them in when().thenReturn() to avoid UnfinishedStubbingException
      RowIterator<Row> alertIterator = createMockRowIterator(Arrays.asList(alertRow));
      RowIterator<Row> scopeIterator = createMockRowIterator(Arrays.asList(scopeRow));

      when(alertRowSet.size()).thenReturn(1);
      when(alertRowSet.iterator()).thenReturn(alertIterator);

      when(scopeRowSet.iterator()).thenReturn(scopeIterator);

      PreparedQuery<RowSet<Row>> alertPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> scopePq = mock(PreparedQuery.class);

      when(writerPool.preparedQuery(anyString()))
          .thenReturn(alertPq)
          .thenReturn(scopePq);
      when(alertPq.rxExecute(any(Tuple.class))).thenReturn(Single.just(alertRowSet));
      when(scopePq.rxExecute(any(Tuple.class))).thenReturn(Single.just(scopeRowSet));

      Alert result = alertsDao.getAlertDetails(1).blockingGet();

      assertNotNull(result);
      assertEquals(1, result.getAlertId());
      assertEquals("Test Alert", result.getName());
    }

    @Test
    void shouldThrowNotFoundWhenAlertNotExists() {
      setupWriterPool();

      RowSet<Row> alertRowSet = mock(RowSet.class);
      when(alertRowSet.size()).thenReturn(0);

      PreparedQuery<RowSet<Row>> alertPq = mock(PreparedQuery.class);
      when(writerPool.preparedQuery(anyString())).thenReturn(alertPq);
      when(alertPq.rxExecute(any(Tuple.class))).thenReturn(Single.just(alertRowSet));

      Exception ex = assertThrows(WebApplicationException.class,
          () -> alertsDao.getAlertDetails(999).blockingGet());
      assertEquals("Alert not found", ex.getMessage());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getAlertDetails(1).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetAlerts {

    @Test
    void shouldGetAlertsSuccessfullyWithResults() {
      setupWriterPool();
      Row alertRow = createMockAlertRow();
      when(alertRow.getInteger("total_count")).thenReturn(1);

      Row scopeRow = createMockScopeRow();

      RowSet<Row> alertRowSet = mock(RowSet.class);
      RowSet<Row> scopeRowSet = mock(RowSet.class);

      // Create mock iterators BEFORE using them in when().thenReturn()
      RowIterator<Row> alertIterator = createMockRowIterator(Arrays.asList(alertRow));
      RowIterator<Row> scopeIterator = createMockRowIterator(Arrays.asList(scopeRow));

      when(alertRowSet.size()).thenReturn(1);
      when(alertRowSet.iterator()).thenReturn(alertIterator);

      when(scopeRowSet.iterator()).thenReturn(scopeIterator);

      PreparedQuery<RowSet<Row>> alertPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> scopePq = mock(PreparedQuery.class);

      when(writerPool.preparedQuery(anyString()))
          .thenReturn(alertPq)
          .thenReturn(scopePq);
      when(alertPq.rxExecute(any(Tuple.class))).thenReturn(Single.just(alertRowSet));
      when(scopePq.rxExecute(any(Tuple.class))).thenReturn(Single.just(scopeRowSet));

      GetAlertsResponse result = alertsDao.getAlerts("Test", "Interaction", 10, 0, "user1", "user2", null).blockingGet();

      assertNotNull(result);
      assertEquals(1, result.getTotalAlerts());
      assertEquals(1, result.getAlerts().size());
    }

    @Test
    void shouldReturnEmptyResponseWhenNoAlertsFound() {
      setupPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      GetAlertsResponse result = alertsDao.getAlerts(null, null, 10, 0, null, null, null).blockingGet();

      assertNotNull(result);
      assertEquals(0, result.getTotalAlerts());
      assertTrue(result.getAlerts().isEmpty());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getAlerts("Test", null, 10, 0, null, null, null).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetAllAlerts {

    @Test
    void shouldGetAllAlertsSuccessfully() {
      setupWriterPool();
      Row alertRow = createMockAlertRow();
      Row scopeRow = createMockScopeRow();

      RowSet<Row> alertRowSet = mock(RowSet.class);
      RowSet<Row> scopeRowSet = mock(RowSet.class);

      // Create mock iterators BEFORE using them in when().thenReturn()
      RowIterator<Row> alertIterator = createMockRowIterator(Arrays.asList(alertRow));
      RowIterator<Row> scopeIterator = createMockRowIterator(Arrays.asList(scopeRow));

      when(alertRowSet.iterator()).thenReturn(alertIterator);

      when(scopeRowSet.iterator()).thenReturn(scopeIterator);

      PreparedQuery<RowSet<Row>> alertPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> scopePq = mock(PreparedQuery.class);

      when(writerPool.preparedQuery(anyString()))
          .thenReturn(alertPq)
          .thenReturn(scopePq);
      when(alertPq.rxExecute()).thenReturn(Single.just(alertRowSet));
      when(scopePq.rxExecute()).thenReturn(Single.just(scopeRowSet));

      GetAllAlertsResponse result = alertsDao.getAllAlerts().blockingGet();

      assertNotNull(result);
      assertNotNull(result.getAlerts());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute())
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getAllAlerts().blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetAlertSeverities {

    @Test
    void shouldGetSeveritiesSuccessfully() {
      setupPreparedQuery();

      Row severityRow = mock(Row.class);
      when(severityRow.getInteger("severity_id")).thenReturn(1);
      when(severityRow.getInteger("name")).thenReturn(1);
      when(severityRow.getString("description")).thenReturn("Critical");

      // Use helper method to properly mock RowSet with forEach support
      setupRowSetMock(rowSet, Arrays.asList(severityRow));
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      List<AlertSeverityResponseDto> result = alertsDao.getAlertSeverities().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals(1, result.get(0).getSeverityId());
    }

    @Test
    void shouldReturnEmptyListWhenNoSeverities() {
      setupPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      List<AlertSeverityResponseDto> result = alertsDao.getAlertSeverities().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute())
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getAlertSeverities().blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestCreateAlertSeverity {

    @Test
    void shouldCreateSeveritySuccessfully() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.createAlertSeverity(1, "Critical").blockingGet();
      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenNoRowsInserted() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.createAlertSeverity(1, "Critical").blockingGet();
      assertFalse(result);
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.createAlertSeverity(1, "Critical").blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetNotificationChannels {

    @Test
    void shouldGetNotificationChannelsSuccessfully() {
      setupPreparedQuery();

      Row channelRow = mock(Row.class);
      when(channelRow.getInteger("notification_channel_id")).thenReturn(1);
      when(channelRow.getString("name")).thenReturn("Slack");
      when(channelRow.getString("notification_webhook_url")).thenReturn("https://slack.webhook");

      // Use helper method to properly mock RowSet with forEach support
      setupRowSetMock(rowSet, Arrays.asList(channelRow));
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      List<AlertNotificationChannelResponseDto> result = alertsDao.getNotificationChannels().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("Slack", result.get(0).getName());
    }

    @Test
    void shouldReturnEmptyListWhenNoChannels() {
      setupPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      List<AlertNotificationChannelResponseDto> result = alertsDao.getNotificationChannels().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute())
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getNotificationChannels().blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestCreateNotificationChannel {

    @Test
    void shouldCreateChannelSuccessfully() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.createNotificationChannel("Slack", "https://webhook").blockingGet();
      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenNoRowsInserted() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.createNotificationChannel("Slack", "https://webhook").blockingGet();
      assertFalse(result);
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.createNotificationChannel("Slack", "https://webhook").blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestCreateTagForAlert {

    @Test
    void shouldCreateTagSuccessfully() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.createTagForAlert("production").blockingGet();
      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenNoRowsInserted() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.createTagForAlert("production").blockingGet();
      assertFalse(result);
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.createTagForAlert("production").blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestCreateTagAndAlertMapping {

    @Test
    void shouldCreateMappingSuccessfully() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.createTagAndAlertMapping(1, 1).blockingGet();
      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenNoRowsInserted() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.createTagAndAlertMapping(1, 1).blockingGet();
      assertFalse(result);
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.createTagAndAlertMapping(1, 1).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetAllTags {

    @Test
    void shouldGetAllTagsSuccessfully() {
      setupWriterPool();
      when(writerPool.query(anyString())).thenReturn(query);

      Row tagRow = mock(Row.class);
      when(tagRow.getInteger("tag_id")).thenReturn(1);
      when(tagRow.getString("name")).thenReturn("production");

      // Use helper method to properly mock RowSet with forEach support
      setupRowSetMock(rowSet, Arrays.asList(tagRow));
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<AlertTagsResponseDto> result = alertsDao.getAllTags().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("production", result.get(0).getName());
    }

    @Test
    void shouldReturnEmptyListWhenNoTags() {
      setupWriterPool();
      when(writerPool.query(anyString())).thenReturn(query);
      when(rowSet.size()).thenReturn(0);
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<AlertTagsResponseDto> result = alertsDao.getAllTags().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupWriterPool();
      when(writerPool.query(anyString())).thenReturn(query);
      when(query.rxExecute())
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getAllTags().blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetTagsByAlertId {

    @Test
    void shouldGetTagsSuccessfully() {
      setupPreparedQuery();

      Row tagRow = mock(Row.class);
      when(tagRow.getInteger("tag_id")).thenReturn(1);
      when(tagRow.getString("name")).thenReturn("production");

      // Use helper method to properly mock RowSet with forEach support
      setupRowSetMock(rowSet, Arrays.asList(tagRow));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<AlertTagsResponseDto> result = alertsDao.getTagsByAlertId(1).blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
    }

    @Test
    void shouldThrowNotFoundWhenNoTags() {
      setupPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Exception ex = assertThrows(WebApplicationException.class,
          () -> alertsDao.getTagsByAlertId(1).blockingGet());
      assertEquals("No tags found", ex.getMessage());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getTagsByAlertId(1).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestDeleteAlertTagMapping {

    @Test
    void shouldDeleteMappingSuccessfully() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.deleteAlertTagMapping(1, 1).blockingGet();
      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenNoRowsDeleted() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.deleteAlertTagMapping(1, 1).blockingGet();
      assertFalse(result);
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.deleteAlertTagMapping(1, 1).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetAlertsFilters {

    @Test
    void shouldGetFiltersSuccessfully() {
      setupPreparedQuery();

      Row filterRow = mock(Row.class);
      when(filterRow.getString("created_by")).thenReturn("user1");
      when(filterRow.getString("updated_by")).thenReturn("user2");
      when(filterRow.getString("current_state")).thenReturn("NORMAL");

      // Create mock iterator BEFORE using it in when().thenReturn()
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(filterRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      AlertFiltersResponseDto result = alertsDao.getAlertsFilters().blockingGet();

      assertNotNull(result);
      assertTrue(result.getCreatedBy().contains("user1"));
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute())
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getAlertsFilters().blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetMetricsByScope {

    @Test
    void shouldGetMetricsSuccessfully() {
      setupPreparedQuery();

      Row metricRow = mock(Row.class);
      when(metricRow.getInteger("id")).thenReturn(1);
      when(metricRow.getString("name")).thenReturn("APDEX");
      when(metricRow.getString("label")).thenReturn("Apdex Score");

      // Create mock iterator BEFORE using it in when().thenReturn()
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(metricRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<MetricItemDto> result = alertsDao.getMetricsByScope("Interaction").blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("APDEX", result.get(0).getName());
    }

    @Test
    void shouldReturnEmptyListWhenNoMetrics() {
      setupPreparedQuery();
      // Create mock iterator BEFORE using it in when().thenReturn()
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<MetricItemDto> result = alertsDao.getMetricsByScope("Interaction").blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getMetricsByScope("Interaction").blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetAlertScopes {

    @Test
    void shouldGetScopesSuccessfully() {
      setupPreparedQuery();

      Row scopeRow = mock(Row.class);
      when(scopeRow.getInteger("id")).thenReturn(1);
      when(scopeRow.getString("name")).thenReturn("Interaction");
      when(scopeRow.getString("label")).thenReturn("User Interaction");

      // Create mock iterator BEFORE using it in when().thenReturn()
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(scopeRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      List<AlertScopeItemDto> result = alertsDao.getAlertScopes().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("Interaction", result.get(0).getName());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute())
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getAlertScopes().blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetAlertDetailsForEvaluation {

    @Test
    void shouldGetDetailsSuccessfully() {
      setupPreparedQuery();

      Row detailRow = mock(Row.class);
      when(detailRow.getInteger("id")).thenReturn(1);
      when(detailRow.getString("name")).thenReturn("Test Alert");
      when(detailRow.getString("description")).thenReturn("Description");
      when(detailRow.getString("scope")).thenReturn("Interaction");
      when(detailRow.getValue("dimension_filter")).thenReturn(null);
      when(detailRow.getString("condition_expression")).thenReturn("A > 100");
      when(detailRow.getInteger("severity_id")).thenReturn(1);
      when(detailRow.getInteger("notification_channel_id")).thenReturn(1);
      when(detailRow.getInteger("evaluation_period")).thenReturn(300);
      when(detailRow.getInteger("evaluation_interval")).thenReturn(60);
      when(detailRow.getString("created_by")).thenReturn("user1");
      when(detailRow.getString("updated_by")).thenReturn("user2");
      when(detailRow.getBoolean("is_active")).thenReturn(true);
      when(detailRow.getLocalDateTime("snoozed_from")).thenReturn(null);
      when(detailRow.getLocalDateTime("snoozed_until")).thenReturn(null);

      // Create mock iterator BEFORE using it in when().thenReturn()
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(detailRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      AlertsDao.AlertDetails result = alertsDao.getAlertDetailsForEvaluation(1).blockingGet();

      assertNotNull(result);
      assertEquals(1, result.getId());
      assertEquals("Test Alert", result.getName());
    }

    @Test
    void shouldThrowNotFoundWhenNoAlert() {
      setupPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Exception ex = assertThrows(WebApplicationException.class,
          () -> alertsDao.getAlertDetailsForEvaluation(999).blockingGet());
      assertEquals("Alert not found", ex.getMessage());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getAlertDetailsForEvaluation(1).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetAlertScopesForEvaluation {

    @Test
    void shouldGetScopesForEvaluationSuccessfully() {
      setupPreparedQuery();

      Row scopeRow = mock(Row.class);
      when(scopeRow.getInteger("id")).thenReturn(1);
      when(scopeRow.getInteger("alert_id")).thenReturn(1);
      when(scopeRow.getString("name")).thenReturn("scope1");
      when(scopeRow.getValue("conditions")).thenReturn("[]");
      when(scopeRow.getString("state")).thenReturn("NORMAL");

      // Create mock iterator BEFORE using it in when().thenReturn()
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(scopeRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<AlertsDao.AlertScopeDetails> result = alertsDao.getAlertScopesForEvaluation(1).blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("scope1", result.get(0).getName());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getAlertScopesForEvaluation(1).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestUpdateScopeState {

    @Test
    void shouldUpdateStateSuccessfully() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.updateScopeState(1, AlertState.FIRING).blockingGet();
      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenNoRowsUpdated() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.updateScopeState(1, AlertState.FIRING).blockingGet();
      assertFalse(result);
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.updateScopeState(1, AlertState.FIRING).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestCreateEvaluationHistory {

    @Test
    void shouldCreateHistorySuccessfully() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.createEvaluationHistory(1, "{}", AlertState.NORMAL).blockingGet();
      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenNoRowsInserted() {
      setupPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = alertsDao.createEvaluationHistory(1, "{}", AlertState.NORMAL).blockingGet();
      assertFalse(result);
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.createEvaluationHistory(1, "{}", AlertState.NORMAL).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetScopeState {

    @Test
    void shouldGetStateSuccessfully() {
      setupPreparedQuery();

      Row stateRow = mock(Row.class);
      when(stateRow.getString("state")).thenReturn("FIRING");

      // Create mock iterator BEFORE using it in when().thenReturn()
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(stateRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      AlertState result = alertsDao.getScopeState(1).blockingGet();

      assertEquals(AlertState.FIRING, result);
    }

    @Test
    void shouldThrowNullPointerWhenNoScope() {
      // RxJava3 Single does not allow null values, so returning null throws NullPointerException
      setupPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertThrows(NullPointerException.class,
          () -> alertsDao.getScopeState(999).blockingGet());
    }

    @Test
    void shouldThrowNullPointerWhenStateIsNull() {
      // RxJava3 Single does not allow null values, so returning null throws NullPointerException
      setupPreparedQuery();

      Row stateRow = mock(Row.class);
      when(stateRow.getString("state")).thenReturn(null);

      // Create mock iterator BEFORE using it in when().thenReturn()
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(stateRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertThrows(NullPointerException.class,
          () -> alertsDao.getScopeState(1).blockingGet());
    }

    @Test
    void shouldThrowNullPointerForInvalidState() {
      // RxJava3 Single does not allow null values, so returning null throws NullPointerException
      setupPreparedQuery();

      Row stateRow = mock(Row.class);
      when(stateRow.getString("state")).thenReturn("INVALID_STATE");

      // Create mock iterator BEFORE using it in when().thenReturn()
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(stateRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertThrows(NullPointerException.class,
          () -> alertsDao.getScopeState(1).blockingGet());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getScopeState(1).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetEvaluationHistoryByAlert {

    @Test
    void shouldGetHistorySuccessfully() {
      setupPreparedQuery();

      Row historyRow = mock(Row.class);
      when(historyRow.getInteger("scope_id")).thenReturn(1);
      when(historyRow.getString("scope_name")).thenReturn("scope1");
      when(historyRow.getInteger("evaluation_id")).thenReturn(1);
      when(historyRow.getValue("evaluation_result")).thenReturn("{}");
      when(historyRow.getString("state")).thenReturn("NORMAL");
      when(historyRow.getLocalDateTime("evaluated_at")).thenReturn(LocalDateTime.now());

      // Create mock iterator BEFORE using it in when().thenReturn()
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(historyRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ScopeEvaluationHistoryDto> result = alertsDao.getEvaluationHistoryByAlert(1).blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("scope1", result.get(0).getScopeName());
    }

    @Test
    void shouldReturnEmptyListWhenNoHistory() {
      setupPreparedQuery();
      // Create mock iterator BEFORE using it in when().thenReturn()
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ScopeEvaluationHistoryDto> result = alertsDao.getEvaluationHistoryByAlert(1).blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.getEvaluationHistoryByAlert(1).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }

    @Test
    void shouldGroupHistoryByScopeId() {
      setupPreparedQuery();

      Row historyRow1 = mock(Row.class);
      when(historyRow1.getInteger("scope_id")).thenReturn(1);
      when(historyRow1.getString("scope_name")).thenReturn("scope1");
      when(historyRow1.getInteger("evaluation_id")).thenReturn(1);
      when(historyRow1.getValue("evaluation_result")).thenReturn("{}");
      when(historyRow1.getString("state")).thenReturn("NORMAL");
      when(historyRow1.getLocalDateTime("evaluated_at")).thenReturn(LocalDateTime.now());

      Row historyRow2 = mock(Row.class);
      when(historyRow2.getInteger("scope_id")).thenReturn(1);
      when(historyRow2.getString("scope_name")).thenReturn("scope1");
      when(historyRow2.getInteger("evaluation_id")).thenReturn(2);
      when(historyRow2.getValue("evaluation_result")).thenReturn("{}");
      when(historyRow2.getString("state")).thenReturn("FIRING");
      when(historyRow2.getLocalDateTime("evaluated_at")).thenReturn(LocalDateTime.now());

      // Create mock iterator BEFORE using it in when().thenReturn()
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(historyRow1, historyRow2));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ScopeEvaluationHistoryDto> result = alertsDao.getEvaluationHistoryByAlert(1).blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals(2, result.get(0).getEvaluationHistory().size());
    }
  }

  @Nested
  class TestCreateAlert {

    @Test
    void shouldThrowExceptionWhenNoScopeNamesInThreshold() {
      Map<String, Float> emptyThreshold = new HashMap<>();
      AlertCondition condition = AlertCondition.builder()
          .alias("A")
          .metric(Metric.APDEX)
          .metricOperator(MetricOperator.GREATER_THAN)
          .threshold(emptyThreshold)
          .build();

      CreateAlertRequest request = CreateAlertRequest.builder()
          .name("Test Alert")
          .description("Test Description")
          .scope(AlertScope.interaction)
          .dimensionFilters("{}")
          .conditionExpression("A > 100")
          .severity(1)
          .notificationChannelId(1)
          .evaluationPeriod(300)
          .evaluationInterval(60)
          .createdBy("user1")
          .alerts(Arrays.asList(condition))
          .build();

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.createAlert(request).blockingGet());
      assertEquals("No scope names found in threshold maps", ex.getMessage());
    }

    @Test
    void shouldCreateAlertSuccessfully() {
      Map<String, Float> threshold = new HashMap<>();
      threshold.put("scope1", 0.5f);

      AlertCondition condition = AlertCondition.builder()
          .alias("A")
          .metric(Metric.APDEX)
          .metricOperator(MetricOperator.GREATER_THAN)
          .threshold(threshold)
          .build();

      CreateAlertRequest request = CreateAlertRequest.builder()
          .name("Test Alert")
          .description("Test Description")
          .scope(AlertScope.interaction)
          .dimensionFilters("{}")
          .conditionExpression("A > 100")
          .severity(1)
          .notificationChannelId(1)
          .evaluationPeriod(300)
          .evaluationInterval(60)
          .createdBy("user1")
          .alerts(Arrays.asList(condition))
          .build();

      setupWriterPool();
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.rxBegin()).thenReturn(Single.just(transaction));

      RowSet<Row> alertRowSet = mock(RowSet.class);
      when(alertRowSet.rowCount()).thenReturn(1);
      when(alertRowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(1L);

      RowSet<Row> scopeRowSet = mock(RowSet.class);
      when(scopeRowSet.rowCount()).thenReturn(1);

      PreparedQuery<RowSet<Row>> alertPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> scopePq = mock(PreparedQuery.class);

      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(alertPq)
          .thenReturn(scopePq);
      when(alertPq.rxExecute(any(Tuple.class))).thenReturn(Single.just(alertRowSet));
      when(scopePq.rxExecute(any(Tuple.class))).thenReturn(Single.just(scopeRowSet));
      when(transaction.rxCommit()).thenReturn(Completable.complete());

      Integer result = alertsDao.createAlert(request).blockingGet();

      assertEquals(1, result);
    }

    @Test
    void shouldRollbackOnError() {
      Map<String, Float> threshold = new HashMap<>();
      threshold.put("scope1", 0.5f);

      AlertCondition condition = AlertCondition.builder()
          .alias("A")
          .metric(Metric.APDEX)
          .metricOperator(MetricOperator.GREATER_THAN)
          .threshold(threshold)
          .build();

      CreateAlertRequest request = CreateAlertRequest.builder()
          .name("Test Alert")
          .description("Test Description")
          .scope(AlertScope.interaction)
          .dimensionFilters("{}")
          .conditionExpression("A > 100")
          .severity(1)
          .notificationChannelId(1)
          .evaluationPeriod(300)
          .evaluationInterval(60)
          .createdBy("user1")
          .alerts(Arrays.asList(condition))
          .build();

      setupWriterPool();
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.rxBegin()).thenReturn(Single.just(transaction));

      PreparedQuery<RowSet<Row>> alertPq = mock(PreparedQuery.class);
      when(sqlConnection.preparedQuery(anyString())).thenReturn(alertPq);
      when(alertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));
      when(transaction.rxRollback()).thenReturn(Completable.complete());

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.createAlert(request).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestUpdateAlert {

    @Test
    void shouldThrowExceptionWhenNoScopeNamesInThreshold() {
      Map<String, Float> emptyThreshold = new HashMap<>();
      AlertCondition condition = AlertCondition.builder()
          .alias("A")
          .metric(Metric.APDEX)
          .metricOperator(MetricOperator.GREATER_THAN)
          .threshold(emptyThreshold)
          .build();

      UpdateAlertRequest request = UpdateAlertRequest.builder()
          .alertId(1)
          .name("Updated Alert")
          .description("Updated Description")
          .scope(AlertScope.interaction)
          .dimensionFilters("{}")
          .conditionExpression("A > 200")
          .severity(2)
          .notificationChannelId(1)
          .evaluationPeriod(600)
          .evaluationInterval(120)
          .updatedBy("user2")
          .createdBy("user1")
          .alerts(Arrays.asList(condition))
          .build();

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.updateAlert(request).blockingGet());
      assertEquals("No scope names found in threshold maps", ex.getMessage());
    }

    @Test
    void shouldUpdateAlertSuccessfully() {
      Map<String, Float> threshold = new HashMap<>();
      threshold.put("scope1", 0.7f);

      AlertCondition condition = AlertCondition.builder()
          .alias("A")
          .metric(Metric.APDEX)
          .metricOperator(MetricOperator.GREATER_THAN)
          .threshold(threshold)
          .build();

      UpdateAlertRequest request = UpdateAlertRequest.builder()
          .alertId(1)
          .name("Updated Alert")
          .description("Updated Description")
          .scope(AlertScope.interaction)
          .dimensionFilters("{}")
          .conditionExpression("A > 200")
          .severity(2)
          .notificationChannelId(1)
          .evaluationPeriod(600)
          .evaluationInterval(120)
          .updatedBy("user2")
          .createdBy("user1")
          .alerts(Arrays.asList(condition))
          .build();

      setupWriterPool();
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.rxBegin()).thenReturn(Single.just(transaction));

      RowSet<Row> updateRowSet = mock(RowSet.class);
      when(updateRowSet.rowCount()).thenReturn(1);

      RowSet<Row> deleteRowSet = mock(RowSet.class);
      when(deleteRowSet.rowCount()).thenReturn(1);

      RowSet<Row> scopeRowSet = mock(RowSet.class);
      when(scopeRowSet.rowCount()).thenReturn(1);

      PreparedQuery<RowSet<Row>> updatePq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> deletePq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> scopePq = mock(PreparedQuery.class);

      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(updatePq)
          .thenReturn(deletePq)
          .thenReturn(scopePq);
      when(updatePq.rxExecute(any(Tuple.class))).thenReturn(Single.just(updateRowSet));
      when(deletePq.rxExecute(any(Tuple.class))).thenReturn(Single.just(deleteRowSet));
      when(scopePq.rxExecute(any(Tuple.class))).thenReturn(Single.just(scopeRowSet));
      when(transaction.rxCommit()).thenReturn(Completable.complete());

      Integer result = alertsDao.updateAlert(request).blockingGet();

      assertEquals(1, result);
    }

    @Test
    void shouldThrowExceptionWhenAlertNotFound() {
      Map<String, Float> threshold = new HashMap<>();
      threshold.put("scope1", 0.7f);

      AlertCondition condition = AlertCondition.builder()
          .alias("A")
          .metric(Metric.APDEX)
          .metricOperator(MetricOperator.GREATER_THAN)
          .threshold(threshold)
          .build();

      UpdateAlertRequest request = UpdateAlertRequest.builder()
          .alertId(999)
          .name("Updated Alert")
          .description("Updated Description")
          .scope(AlertScope.interaction)
          .dimensionFilters("{}")
          .conditionExpression("A > 200")
          .severity(2)
          .notificationChannelId(1)
          .evaluationPeriod(600)
          .evaluationInterval(120)
          .updatedBy("user2")
          .createdBy("user1")
          .alerts(Arrays.asList(condition))
          .build();

      setupWriterPool();
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.rxBegin()).thenReturn(Single.just(transaction));

      RowSet<Row> updateRowSet = mock(RowSet.class);
      when(updateRowSet.rowCount()).thenReturn(0);

      PreparedQuery<RowSet<Row>> updatePq = mock(PreparedQuery.class);
      when(sqlConnection.preparedQuery(anyString())).thenReturn(updatePq);
      when(updatePq.rxExecute(any(Tuple.class))).thenReturn(Single.just(updateRowSet));
      when(transaction.rxRollback()).thenReturn(Completable.complete());

      Exception ex = assertThrows(WebApplicationException.class,
          () -> alertsDao.updateAlert(request).blockingGet());
      assertEquals("Alert not found or not updated", ex.getMessage());
    }

    @Test
    void shouldRollbackOnError() {
      Map<String, Float> threshold = new HashMap<>();
      threshold.put("scope1", 0.7f);

      AlertCondition condition = AlertCondition.builder()
          .alias("A")
          .metric(Metric.APDEX)
          .metricOperator(MetricOperator.GREATER_THAN)
          .threshold(threshold)
          .build();

      UpdateAlertRequest request = UpdateAlertRequest.builder()
          .alertId(1)
          .name("Updated Alert")
          .description("Updated Description")
          .scope(AlertScope.interaction)
          .dimensionFilters("{}")
          .conditionExpression("A > 200")
          .severity(2)
          .notificationChannelId(1)
          .evaluationPeriod(600)
          .evaluationInterval(120)
          .updatedBy("user2")
          .createdBy("user1")
          .alerts(Arrays.asList(condition))
          .build();

      setupWriterPool();
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.rxBegin()).thenReturn(Single.just(transaction));

      PreparedQuery<RowSet<Row>> updatePq = mock(PreparedQuery.class);
      when(sqlConnection.preparedQuery(anyString())).thenReturn(updatePq);
      when(updatePq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));
      when(transaction.rxRollback()).thenReturn(Completable.complete());

      Exception ex = assertThrows(RuntimeException.class,
          () -> alertsDao.updateAlert(request).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }
}
