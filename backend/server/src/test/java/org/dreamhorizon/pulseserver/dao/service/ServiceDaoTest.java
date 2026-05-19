package org.dreamhorizon.pulseserver.dao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.service.models.ServiceRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class ServiceDaoTest {

  @Mock MysqlClient mysqlClient;
  @Mock MySQLPool readerPool;
  @Mock MySQLPool writerPool;
  @Mock PreparedQuery<RowSet<Row>> preparedQuery;
  @Mock RowSet<Row> rowSet;
  @Mock RowIterator<Row> rowIterator;
  @Mock Row row;

  ServiceDao dao;

  @BeforeEach
  void setUp() {
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    dao = new ServiceDao(mysqlClient);
  }

  private void mockReaderQuery() {
    when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private void mockWriterQuery() {
    when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private void setupRowData() {
    when(row.getLong("id")).thenReturn(1L);
    when(row.getString("service_name")).thenReturn("payment-service");
    when(row.getString("service_group")).thenReturn("payments");
    when(row.getString("display_name")).thenReturn("Payment Service");
    when(row.getString("owner_email")).thenReturn("owner@test.com");
    when(row.getString("owner_slack_id")).thenReturn("U123");
    when(row.getString("goalert_service_id")).thenReturn("goalert-uuid");
    when(row.getString("description")).thenReturn("Handles payments");
    when(row.getBoolean("is_active")).thenReturn(true);
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
    when(row.getLocalDateTime("created_at")).thenReturn(now);
    when(row.getLocalDateTime("updated_at")).thenReturn(now);
  }

  @Nested
  class GetByServiceName {

    @Test
    void shouldReturnServiceWhenFound() {
      mockReaderQuery();
      setupRowData();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(rowIterator.next()).thenReturn(row);

      ServiceRow result = dao.getByServiceName("payment-service").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getServiceName()).isEqualTo("payment-service");
      assertThat(result.getOwnerEmail()).isEqualTo("owner@test.com");
      assertThat(result.getGoalertServiceId()).isEqualTo("goalert-uuid");
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
      mockReaderQuery();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.size()).thenReturn(0);

      dao.getByServiceName("nonexistent")
          .test()
          .assertComplete()
          .assertNoValues();
    }

    @Test
    void shouldPropagateError() {
      mockReaderQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("db error")));

      dao.getByServiceName("payment-service")
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class GetAllActive {

    @Test
    void shouldReturnAllActiveServices() {
      mockReaderQuery();
      setupRowData();
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(rowIterator.hasNext()).thenReturn(true, false);
      when(rowIterator.next()).thenReturn(row);

      List<ServiceRow> result = dao.getAllActive().blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getServiceName()).isEqualTo("payment-service");
    }

    @Test
    void shouldReturnEmptyListWhenNoServices() {
      mockReaderQuery();
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(rowIterator.hasNext()).thenReturn(false);

      List<ServiceRow> result = dao.getAllActive().blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldPropagateError() {
      mockReaderQuery();
      when(preparedQuery.rxExecute())
          .thenReturn(Single.error(new RuntimeException("db down")));

      dao.getAllActive()
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class Create {

    @Test
    void shouldCreateAndReturnWithId() {
      mockWriterQuery();
      ServiceRow input = ServiceRow.builder()
          .serviceName("new-service")
          .serviceGroup("core")
          .displayName("New Service")
          .ownerEmail("dev@test.com")
          .ownerSlackId("U456")
          .goalertServiceId("goalert-new")
          .description("A new service")
          .build();

      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.property(any())).thenReturn(42L);

      ServiceRow result = dao.create(input).blockingGet();

      assertThat(result.getId()).isEqualTo(42L);
      assertThat(result.getServiceName()).isEqualTo("new-service");
      assertThat(result.getIsActive()).isTrue();
    }

    @Test
    void shouldPropagateErrorOnCreate() {
      mockWriterQuery();
      ServiceRow input = ServiceRow.builder().serviceName("fail-service").build();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("duplicate key")));

      dao.create(input)
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class Update {

    @Test
    void shouldUpdateExistingService() {
      mockWriterQuery();
      ServiceRow input = ServiceRow.builder()
          .serviceGroup("updated-group")
          .displayName("Updated Name")
          .ownerEmail("new@test.com")
          .ownerSlackId("U789")
          .goalertServiceId("goalert-updated")
          .description("Updated desc")
          .build();

      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.rowCount()).thenReturn(1);

      ServiceRow result = dao.update("payment-service", input).blockingGet();

      assertThat(result.getServiceName()).isEqualTo("payment-service");
      assertThat(result.getServiceGroup()).isEqualTo("updated-group");
    }

    @Test
    void shouldErrorWhenServiceNotFoundOnUpdate() {
      mockWriterQuery();
      ServiceRow input = ServiceRow.builder().build();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.rowCount()).thenReturn(0);

      dao.update("nonexistent", input)
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class SoftDelete {

    @Test
    void shouldCompleteOnSuccessfulDelete() {
      mockWriterQuery();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.rowCount()).thenReturn(1);

      dao.softDelete("payment-service")
          .test()
          .assertComplete();
    }

    @Test
    void shouldCompleteEvenWhenNoRowsAffected() {
      mockWriterQuery();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.rowCount()).thenReturn(0);

      dao.softDelete("nonexistent")
          .test()
          .assertComplete();
    }

    @Test
    void shouldPropagateError() {
      mockWriterQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("db error")));

      dao.softDelete("payment-service")
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class MapRow {

    @Test
    void shouldHandleNullTimestamps() {
      mockReaderQuery();
      when(row.getLong("id")).thenReturn(1L);
      when(row.getString("service_name")).thenReturn("svc");
      when(row.getString("service_group")).thenReturn(null);
      when(row.getString("display_name")).thenReturn(null);
      when(row.getString("owner_email")).thenReturn("a@b.com");
      when(row.getString("owner_slack_id")).thenReturn(null);
      when(row.getString("goalert_service_id")).thenReturn(null);
      when(row.getString("description")).thenReturn(null);
      when(row.getBoolean("is_active")).thenReturn(true);
      when(row.getLocalDateTime("created_at")).thenReturn(null);
      when(row.getLocalDateTime("updated_at")).thenReturn(null);

      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(rowIterator.next()).thenReturn(row);

      ServiceRow result = dao.getByServiceName("svc").blockingGet();

      assertThat(result.getCreatedAt()).isNull();
      assertThat(result.getUpdatedAt()).isNull();
    }
  }
}
