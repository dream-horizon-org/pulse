package org.dreamhorizon.pulseserver.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.DeleteSnoozeRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertRequest;
import org.dreamhorizon.pulseserver.util.DateTimeUtil;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import io.vertx.mysqlclient.MySQLException;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertsDaoTest {

  @Mock
  MysqlClient d11MysqlClient;

  @Mock
  DateTimeUtil dateTimeUtil;

  AlertsDao alertsDao;

  @BeforeEach
  public void setup() {
    alertsDao = new AlertsDao(d11MysqlClient, dateTimeUtil);
  }

  @Nested
  public class TestSnoozeAlerts {

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

      // Mock DateTimeUtil
      when(dateTimeUtil.getLocalDateTime(ZoneOffset.UTC)).thenReturn(now);

      // Mock RowSet
      RowSet rowSet = org.mockito.Mockito.mock(RowSet.class);
      when(rowSet.rowCount()).thenReturn(1);
      // Mock rxExecute
      MySQLPool masterClient = org.mockito.Mockito.mock(MySQLPool.class);
      when(d11MysqlClient.getWriterPool()).thenReturn(masterClient);
      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      when(masterClient.preparedQuery(any())).thenReturn(new PreparedQuery(null, null));
      PreparedQuery pq = org.mockito.Mockito.mock(PreparedQuery.class);
      when(masterClient.preparedQuery(any())).thenReturn(pq);
      when(pq.rxExecute(tupleCaptor.capture())).thenReturn(Single.just(rowSet));

      EmptyResponse resp = alertsDao.snoozeAlert(request).blockingGet();
      assertEquals(EmptyResponse.emptyResponse, resp);

      Tuple tuple = tupleCaptor.getValue();
      assertEquals(now, tuple.getLocalDateTime(0));
      assertEquals(snoozeFrom, tuple.getLocalDateTime(1));
      assertEquals(snoozeUntil, tuple.getLocalDateTime(2));
      assertEquals("test_user", tuple.getString(3));
      assertEquals(42, tuple.getInteger(4));
      verifyNoMoreInteractions(d11MysqlClient, dateTimeUtil);
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
      MySQLPool masterClient = org.mockito.Mockito.mock(
          MySQLPool.class);
      when(d11MysqlClient.getWriterPool()).thenReturn(masterClient);
      PreparedQuery pq = org.mockito.Mockito.mock(PreparedQuery.class);
      when(masterClient.preparedQuery(any())).thenReturn(pq);
      when(pq.rxExecute(any())).thenReturn(Single.error(new MySQLException("DB Error", 400, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class, () -> alertsDao.snoozeAlert(request).blockingGet());
      assertEquals("{errorMessage=DB Error, errorCode=400, sqlState=SQLSTATE}", ex.getMessage());
      verifyNoMoreInteractions(d11MysqlClient, dateTimeUtil);
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
      RowSet rowSet = org.mockito.Mockito.mock(RowSet.class);
      when(rowSet.rowCount()).thenReturn(0);
      MySQLPool masterClient = org.mockito.Mockito.mock(
          MySQLPool.class);
      when(d11MysqlClient.getWriterPool()).thenReturn(masterClient);
      PreparedQuery pq = org.mockito.Mockito.mock(PreparedQuery.class);
      when(masterClient.preparedQuery(any())).thenReturn(pq);
      when(pq.rxExecute(any())).thenReturn(Single.just(rowSet));

      Exception ex = assertThrows(jakarta.ws.rs.WebApplicationException.class, () -> alertsDao.snoozeAlert(request).blockingGet());
      assertEquals("Error while updating alert in db", ex.getMessage());
      verifyNoMoreInteractions(d11MysqlClient, dateTimeUtil);
    }
  }

  @Nested
  public class TestDeleteSnooze {

    @Test
    void shouldDeleteSnoozeSuccessfully() {
      LocalDateTime now = LocalDateTime.of(2024, 1, 1, 0, 0);
      DeleteSnoozeRequest request = DeleteSnoozeRequest.builder()
          .alertId(99)
          .updatedBy("resume_user")
          .build();
      RowSet rowSet = org.mockito.Mockito.mock(RowSet.class);
      when(rowSet.rowCount()).thenReturn(1);
      MySQLPool masterClient = org.mockito.Mockito.mock(
          MySQLPool.class);
      when(d11MysqlClient.getWriterPool()).thenReturn(masterClient);
      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      PreparedQuery pq = org.mockito.Mockito.mock(PreparedQuery.class);
      when(masterClient.preparedQuery(any())).thenReturn(pq);
      when(pq.rxExecute(tupleCaptor.capture())).thenReturn(Single.just(rowSet));

      EmptyResponse resp = alertsDao.deleteSnooze(request).blockingGet();
      assertEquals(EmptyResponse.emptyResponse, resp);

      Tuple tuple = tupleCaptor.getValue();
      assertEquals("resume_user", tuple.getString(0));
      assertEquals(99, tuple.getInteger(1));
      verifyNoMoreInteractions(d11MysqlClient, dateTimeUtil);
    }

    @Test
    void shouldThrowExceptionWhenDatabaseCallThrowsException() {
      LocalDateTime now = LocalDateTime.of(2024, 1, 1, 0, 0);
      DeleteSnoozeRequest request = DeleteSnoozeRequest.builder()
          .alertId(99)
          .updatedBy("resume_user")
          .build();
      MySQLPool masterClient = org.mockito.Mockito.mock(
          MySQLPool.class);
      when(d11MysqlClient.getWriterPool()).thenReturn(masterClient);
      PreparedQuery pq = org.mockito.Mockito.mock(PreparedQuery.class);
      when(masterClient.preparedQuery(any())).thenReturn(pq);
      when(pq.rxExecute(any())).thenReturn(Single.error(new MySQLException("DB Error", 400, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class, () -> alertsDao.deleteSnooze(request).blockingGet());
      assertEquals("{errorMessage=DB Error, errorCode=400, sqlState=SQLSTATE}", ex.getMessage());
      verifyNoMoreInteractions(d11MysqlClient, dateTimeUtil);
    }

    @Test
    void shouldThrowExceptionWhenNoRowsAreUpdated() {
      LocalDateTime now = LocalDateTime.of(2024, 1, 1, 0, 0);
      DeleteSnoozeRequest request = DeleteSnoozeRequest.builder()
          .alertId(99)
          .updatedBy("resume_user")
          .build();
      RowSet rowSet = org.mockito.Mockito.mock(RowSet.class);
      when(rowSet.rowCount()).thenReturn(0);
      MySQLPool masterClient = org.mockito.Mockito.mock(
          MySQLPool.class);
      when(d11MysqlClient.getWriterPool()).thenReturn(masterClient);
      PreparedQuery pq = org.mockito.Mockito.mock(PreparedQuery.class);
      when(masterClient.preparedQuery(any())).thenReturn(pq);
      when(pq.rxExecute(any())).thenReturn(Single.just(rowSet));

      Exception ex = assertThrows(jakarta.ws.rs.WebApplicationException.class, () -> alertsDao.deleteSnooze(request).blockingGet());
      assertEquals("Error while deleting snooze from alert in db", ex.getMessage());
      verifyNoMoreInteractions(d11MysqlClient, dateTimeUtil);
    }
  }
}