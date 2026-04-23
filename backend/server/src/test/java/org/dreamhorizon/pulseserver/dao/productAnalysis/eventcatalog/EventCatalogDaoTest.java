package org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog.models.EventCatalogEventNameRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog.models.EventCatalogFilterKeyRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
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
class EventCatalogDaoTest {

  private static final String PROJECT_ID = "test-project";

  @Mock
  ClickhouseQueryService clickhouseQueryService;

  EventCatalogDao dao;

  @BeforeEach
  void setup() {
    TenantContext.setTenantId(PROJECT_ID);
    dao = new EventCatalogDao(clickhouseQueryService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private EventCatalogEventNameRow nameRow(String name) {
    EventCatalogEventNameRow r = new EventCatalogEventNameRow();
    r.setName(name);
    return r;
  }

  private EventCatalogFilterKeyRow filterKeyRow(String key) {
    EventCatalogFilterKeyRow r = new EventCatalogFilterKeyRow();
    r.setFilterKey(key);
    return r;
  }

  @Nested
  class ListEventNames {
    @Test
    void shouldReturnTrimmedNonEmptyNames() {
      QueryResultResponse<EventCatalogEventNameRow> resp =
          QueryResultResponse.<EventCatalogEventNameRow>builder()
              .rows(Arrays.asList(nameRow("login"), nameRow("  signup  "), nameRow(""), nameRow(null)))
              .build();
      when(clickhouseQueryService.executeQueryOrCreateJob(
          any(QueryConfiguration.class), eq(EventCatalogEventNameRow.class)))
          .thenReturn(Single.just(resp));

      List<String> result = dao.listEventNames(PROJECT_ID).blockingGet();

      assertEquals(2, result.size());
      assertEquals("login", result.get(0));
      assertEquals("signup", result.get(1));
    }

    @Test
    void shouldReturnEmptyOnNullRows() {
      QueryResultResponse<EventCatalogEventNameRow> resp =
          QueryResultResponse.<EventCatalogEventNameRow>builder().rows(null).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(
          any(QueryConfiguration.class), eq(EventCatalogEventNameRow.class)))
          .thenReturn(Single.just(resp));

      List<String> result = dao.listEventNames(PROJECT_ID).blockingGet();
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldPropagateError() {
      when(clickhouseQueryService.executeQueryOrCreateJob(
          any(QueryConfiguration.class), eq(EventCatalogEventNameRow.class)))
          .thenReturn(Single.error(new RuntimeException("ch err")));

      assertThrows(RuntimeException.class, () ->
          dao.listEventNames(PROJECT_ID).blockingGet());
    }
  }

  @Nested
  class ListFilterKeys {
    @Test
    void shouldReturnTrimmedFilterKeys() {
      QueryResultResponse<EventCatalogFilterKeyRow> resp =
          QueryResultResponse.<EventCatalogFilterKeyRow>builder()
              .rows(Arrays.asList(filterKeyRow("OS"), filterKeyRow(" COUNTRY "),
                  filterKeyRow(""), filterKeyRow(null)))
              .build();
      when(clickhouseQueryService.executeQueryOrCreateJob(
          any(QueryConfiguration.class), eq(EventCatalogFilterKeyRow.class)))
          .thenReturn(Single.just(resp));

      List<String> result = dao.listFilterKeys(PROJECT_ID).blockingGet();

      assertEquals(2, result.size());
      assertEquals("OS", result.get(0));
      assertEquals("COUNTRY", result.get(1));
    }

    @Test
    void shouldReturnEmptyOnNullRows() {
      QueryResultResponse<EventCatalogFilterKeyRow> resp =
          QueryResultResponse.<EventCatalogFilterKeyRow>builder().rows(null).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(
          any(QueryConfiguration.class), eq(EventCatalogFilterKeyRow.class)))
          .thenReturn(Single.just(resp));

      assertTrue(dao.listFilterKeys(PROJECT_ID).blockingGet().isEmpty());
    }
  }

  @Nested
  class ListFilterValues {
    @Test
    void shouldReturnFilterValues() {
      QueryResultResponse<EventCatalogEventNameRow> resp =
          QueryResultResponse.<EventCatalogEventNameRow>builder()
              .rows(Collections.singletonList(nameRow("iOS")))
              .build();
      when(clickhouseQueryService.executeQueryOrCreateJob(
          any(QueryConfiguration.class), eq(EventCatalogEventNameRow.class)))
          .thenReturn(Single.just(resp));

      List<String> result = dao.listFilterValues(PROJECT_ID, "OS").blockingGet();

      assertEquals(1, result.size());
      assertEquals("iOS", result.get(0));
    }
  }
}
