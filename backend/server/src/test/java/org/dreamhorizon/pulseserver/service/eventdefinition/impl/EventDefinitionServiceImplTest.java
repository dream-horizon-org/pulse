package org.dreamhorizon.pulseserver.service.eventdefinition.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.WebApplicationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.dao.eventdefinition.EventDefinitionDao;
import org.dreamhorizon.pulseserver.error.EventDefinitionNotFoundException;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.BulkUploadResult;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.CreateEventDefinitionRequest;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventAttributeDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinitionPage;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.UpdateEventDefinitionRequest;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventDefinitionServiceImplTest {

  private static final String TEST_PROJECT_ID = "test-project";
  private static final String TEST_USER = "user@example.com";
  private static final String CSV_HEADER =
      "event_name,event_description,category,attribute_name,"
          + "attribute_description,attribute_type,attribute_required\n";

  @Mock
  private EventDefinitionDao eventDefinitionDao;

  private EventDefinitionServiceImpl service;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(TEST_PROJECT_ID);
    service = new EventDefinitionServiceImpl(eventDefinitionDao);
  }

  private EventDefinition buildEventDefinition(Long id, String eventName) {
    return EventDefinition.builder()
        .id(id)
        .projectId(TEST_PROJECT_ID)
        .eventName(eventName)
        .displayName(eventName)
        .description("Test event")
        .category("test")
        .archived(false)
        .createdBy(TEST_USER)
        .createdAt(Timestamp.valueOf(LocalDateTime.now()))
        .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
        .attributes(Collections.emptyList())
        .build();
  }

  private EventAttributeDefinition buildAttribute(Long id, Long eventDefId, String name) {
    return EventAttributeDefinition.builder()
        .id(id)
        .eventDefinitionId(eventDefId)
        .attributeName(name)
        .description("Test attr")
        .dataType("string")
        .required(false)
        .archived(false)
        .build();
  }

  private InputStream csvStream(String csv) {
    return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
  }

  @Nested
  class TestCreateEventDefinition {

    @Test
    void shouldCreateEventDefinitionSuccessfully() {
      EventDefinition expected = buildEventDefinition(1L, "test_event");

      when(eventDefinitionDao.createEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));
      when(eventDefinitionDao.getEventDefinitionById(1L))
          .thenReturn(Single.just(expected));
      when(eventDefinitionDao.getAttributesForEvent(1L))
          .thenReturn(Single.just(Collections.emptyList()));

      CreateEventDefinitionRequest request = CreateEventDefinitionRequest.builder()
          .eventName("test_event")
          .description("Test event")
          .category("test")
          .user(TEST_USER)
          .build();

      EventDefinition result = service.createEventDefinition(request).blockingGet();

      assertThat(result.getEventName()).isEqualTo("test_event");
      assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void shouldCreateEventDefinitionWithAttributes() {
      List<EventAttributeDefinition> attrs = List.of(
          EventAttributeDefinition.builder()
              .attributeName("count")
              .dataType("int")
              .required(true)
              .build()
      );

      EventDefinition expected = buildEventDefinition(1L, "test_event")
          .toBuilder()
          .attributes(attrs)
          .build();

      when(eventDefinitionDao.createEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));
      when(eventDefinitionDao.getEventDefinitionById(1L))
          .thenReturn(Single.just(expected));
      when(eventDefinitionDao.getAttributesForEvent(1L))
          .thenReturn(Single.just(attrs));

      CreateEventDefinitionRequest request = CreateEventDefinitionRequest.builder()
          .eventName("test_event")
          .description("Test event")
          .attributes(attrs)
          .user(TEST_USER)
          .build();

      EventDefinition result = service.createEventDefinition(request).blockingGet();

      assertThat(result.getAttributes()).hasSize(1);
      assertThat(result.getAttributes().get(0).getAttributeName()).isEqualTo("count");
    }

    @Test
    void shouldMapFieldsCorrectlyToEventDefinition() {
      EventDefinition expected = buildEventDefinition(1L, "mapped_event");

      when(eventDefinitionDao.createEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));
      when(eventDefinitionDao.getEventDefinitionById(1L))
          .thenReturn(Single.just(expected));
      when(eventDefinitionDao.getAttributesForEvent(1L))
          .thenReturn(Single.just(Collections.emptyList()));

      CreateEventDefinitionRequest request = CreateEventDefinitionRequest.builder()
          .eventName("mapped_event")
          .displayName("Mapped Event")
          .description("A mapped event")
          .category("mapping")
          .user(TEST_USER)
          .build();

      service.createEventDefinition(request).blockingGet();

      ArgumentCaptor<EventDefinition> captor =
          ArgumentCaptor.forClass(EventDefinition.class);
      verify(eventDefinitionDao).createEventDefinition(captor.capture());

      EventDefinition captured = captor.getValue();
      assertThat(captured.getEventName()).isEqualTo("mapped_event");
      assertThat(captured.getDisplayName()).isEqualTo("Mapped Event");
      assertThat(captured.getDescription()).isEqualTo("A mapped event");
      assertThat(captured.getCategory()).isEqualTo("mapping");
      assertThat(captured.getCreatedBy()).isEqualTo(TEST_USER);
    }

    @Test
    void shouldWrapDuplicateEntryErrorAs409() {
      when(eventDefinitionDao.createEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.error(
              new RuntimeException("errorCode=1062: Duplicate entry 'test' for key 'uk_project_event'")));

      CreateEventDefinitionRequest request = CreateEventDefinitionRequest.builder()
          .eventName("test_event")
          .user(TEST_USER)
          .build();

      service.createEventDefinition(request)
          .test()
          .assertError(err -> {
            assertThat(err).isInstanceOf(WebApplicationException.class);
            WebApplicationException wae = (WebApplicationException) err;
            assertThat(wae.getResponse().getStatus()).isEqualTo(409);
            return true;
          });
    }

    @Test
    void shouldWrapGenericDaoErrorAsWebException() {
      when(eventDefinitionDao.createEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.error(new RuntimeException("Connection timeout")));

      CreateEventDefinitionRequest request = CreateEventDefinitionRequest.builder()
          .eventName("test_event")
          .user(TEST_USER)
          .build();

      service.createEventDefinition(request)
          .test()
          .assertError(WebApplicationException.class);
    }
  }

  @Nested
  class TestUpdateEventDefinition {

    @Test
    void shouldUpdateEventDefinitionSuccessfully() {
      when(eventDefinitionDao.updateEventDefinition(any(EventDefinition.class)))
          .thenReturn(Completable.complete());

      UpdateEventDefinitionRequest request = UpdateEventDefinitionRequest.builder()
          .id(1L)
          .description("Updated description")
          .user(TEST_USER)
          .build();

      service.updateEventDefinition(request).blockingAwait();

      verify(eventDefinitionDao).updateEventDefinition(any(EventDefinition.class));
    }

    @Test
    void shouldMapUpdateFieldsCorrectly() {
      when(eventDefinitionDao.updateEventDefinition(any(EventDefinition.class)))
          .thenReturn(Completable.complete());

      List<EventAttributeDefinition> attrs = List.of(
          EventAttributeDefinition.builder()
              .attributeName("price")
              .dataType("double")
              .build()
      );

      UpdateEventDefinitionRequest request = UpdateEventDefinitionRequest.builder()
          .id(5L)
          .displayName("Updated Name")
          .description("Updated desc")
          .category("updated-cat")
          .attributes(attrs)
          .user(TEST_USER)
          .build();

      service.updateEventDefinition(request).blockingAwait();

      ArgumentCaptor<EventDefinition> captor =
          ArgumentCaptor.forClass(EventDefinition.class);
      verify(eventDefinitionDao).updateEventDefinition(captor.capture());

      EventDefinition captured = captor.getValue();
      assertThat(captured.getId()).isEqualTo(5L);
      assertThat(captured.getDisplayName()).isEqualTo("Updated Name");
      assertThat(captured.getDescription()).isEqualTo("Updated desc");
      assertThat(captured.getCategory()).isEqualTo("updated-cat");
      assertThat(captured.getAttributes()).hasSize(1);
      assertThat(captured.getUpdatedBy()).isEqualTo(TEST_USER);
    }

    @Test
    void shouldWrapUpdateDaoErrorAsWebException() {
      when(eventDefinitionDao.updateEventDefinition(any(EventDefinition.class)))
          .thenReturn(Completable.error(new RuntimeException("DB write failure")));

      UpdateEventDefinitionRequest request = UpdateEventDefinitionRequest.builder()
          .id(1L)
          .user(TEST_USER)
          .build();

      service.updateEventDefinition(request)
          .test()
          .assertError(WebApplicationException.class);
    }
  }

  @Nested
  class TestGetEventDefinition {

    @Test
    void shouldGetEventDefinitionById() {
      EventDefinition expected = buildEventDefinition(1L, "test_event");

      when(eventDefinitionDao.getEventDefinitionById(1L))
          .thenReturn(Single.just(expected));
      when(eventDefinitionDao.getAttributesForEvent(1L))
          .thenReturn(Single.just(Collections.emptyList()));

      EventDefinition result = service.getEventDefinitionById(1L).blockingGet();

      assertThat(result.getId()).isEqualTo(1L);
      assertThat(result.getEventName()).isEqualTo("test_event");
    }

    @Test
    void shouldEnrichEventWithAttributes() {
      EventDefinition expected = buildEventDefinition(1L, "test_event");
      List<EventAttributeDefinition> attrs = List.of(
          buildAttribute(10L, 1L, "color"),
          buildAttribute(11L, 1L, "size")
      );

      when(eventDefinitionDao.getEventDefinitionById(1L))
          .thenReturn(Single.just(expected));
      when(eventDefinitionDao.getAttributesForEvent(1L))
          .thenReturn(Single.just(attrs));

      EventDefinition result = service.getEventDefinitionById(1L).blockingGet();

      assertThat(result.getAttributes()).hasSize(2);
      assertThat(result.getAttributes().get(0).getAttributeName()).isEqualTo("color");
      assertThat(result.getAttributes().get(1).getAttributeName()).isEqualTo("size");
    }

    @Test
    void shouldReturn404WhenNotFound() {
      when(eventDefinitionDao.getEventDefinitionById(999L))
          .thenReturn(Single.error(new EventDefinitionNotFoundException(999L)));

      service.getEventDefinitionById(999L)
          .test()
          .assertError(err -> {
            assertThat(err).isInstanceOf(WebApplicationException.class);
            WebApplicationException wae = (WebApplicationException) err;
            assertThat(wae.getResponse().getStatus()).isEqualTo(404);
            return true;
          });
    }
  }

  @Nested
  class TestQueryEventDefinitions {

    @Test
    void shouldGetAllEventDefinitions() {
      List<EventDefinition> definitions = List.of(
          buildEventDefinition(1L, "event_a"),
          buildEventDefinition(2L, "event_b")
      );

      EventDefinitionPage page = EventDefinitionPage.builder()
          .definitions(definitions).totalCount(2).build();

      when(eventDefinitionDao.queryEventDefinitions(isNull(), isNull(), eq(50), eq(0)))
          .thenReturn(Single.just(page));
      when(eventDefinitionDao.getAttributesForEvents(any()))
          .thenReturn(Single.just(Collections.emptyMap()));

      EventDefinitionPage result = service.queryEventDefinitions(null, null, 50, 0)
          .blockingGet();

      assertThat(result.getDefinitions()).hasSize(2);
      assertThat(result.getTotalCount()).isEqualTo(2);
    }

    @Test
    void shouldSearchByName() {
      List<EventDefinition> definitions = List.of(
          buildEventDefinition(1L, "cart_viewed")
      );

      EventDefinitionPage page = EventDefinitionPage.builder()
          .definitions(definitions).totalCount(1).build();

      when(eventDefinitionDao.queryEventDefinitions(eq("cart"), isNull(), eq(10), eq(0)))
          .thenReturn(Single.just(page));
      when(eventDefinitionDao.getAttributesForEvents(any()))
          .thenReturn(Single.just(Collections.emptyMap()));

      EventDefinitionPage result = service.queryEventDefinitions("cart", null, 10, 0)
          .blockingGet();

      assertThat(result.getDefinitions()).hasSize(1);
      assertThat(result.getDefinitions().get(0).getEventName()).isEqualTo("cart_viewed");
    }

    @Test
    void shouldSearchByNameAndCategory() {
      List<EventDefinition> definitions = List.of(
          buildEventDefinition(1L, "cart_viewed")
      );

      EventDefinitionPage page = EventDefinitionPage.builder()
          .definitions(definitions).totalCount(1).build();

      when(eventDefinitionDao.queryEventDefinitions(eq("cart"), eq("commerce"), eq(10), eq(0)))
          .thenReturn(Single.just(page));
      when(eventDefinitionDao.getAttributesForEvents(any()))
          .thenReturn(Single.just(Collections.emptyMap()));

      EventDefinitionPage result = service.queryEventDefinitions("cart", "commerce", 10, 0)
          .blockingGet();

      assertThat(result.getDefinitions()).hasSize(1);
      assertThat(result.getDefinitions().get(0).getEventName()).isEqualTo("cart_viewed");
    }

    @Test
    void shouldEnrichAllDefinitionsWithBatchAttributes() {
      EventDefinition def1 = buildEventDefinition(1L, "event_a");
      EventDefinition def2 = buildEventDefinition(2L, "event_b");

      EventDefinitionPage page = EventDefinitionPage.builder()
          .definitions(List.of(def1, def2)).totalCount(2).build();

      Map<Long, List<EventAttributeDefinition>> attrMap = new HashMap<>();
      attrMap.put(1L, List.of(buildAttribute(10L, 1L, "attr_a")));
      attrMap.put(2L, List.of(
          buildAttribute(20L, 2L, "attr_b1"),
          buildAttribute(21L, 2L, "attr_b2")
      ));

      when(eventDefinitionDao.queryEventDefinitions(isNull(), isNull(), eq(50), eq(0)))
          .thenReturn(Single.just(page));
      when(eventDefinitionDao.getAttributesForEvents(List.of(1L, 2L)))
          .thenReturn(Single.just(attrMap));

      EventDefinitionPage result = service.queryEventDefinitions(null, null, 50, 0)
          .blockingGet();

      assertThat(result.getDefinitions().get(0).getAttributes()).hasSize(1);
      assertThat(result.getDefinitions().get(1).getAttributes()).hasSize(2);
    }

    @Test
    void shouldHandleEmptyDefinitionsList() {
      EventDefinitionPage page = EventDefinitionPage.builder()
          .definitions(Collections.emptyList()).totalCount(0).build();

      when(eventDefinitionDao.queryEventDefinitions(isNull(), isNull(), eq(50), eq(0)))
          .thenReturn(Single.just(page));

      EventDefinitionPage result = service.queryEventDefinitions(null, null, 50, 0)
          .blockingGet();

      assertThat(result.getDefinitions()).isEmpty();
      assertThat(result.getTotalCount()).isEqualTo(0);
      verify(eventDefinitionDao, never()).getAttributesForEvents(any());
    }

    @Test
    void shouldSetEmptyAttributesWhenNotInBatchResult() {
      EventDefinition def = buildEventDefinition(1L, "event_lonely");
      EventDefinitionPage page = EventDefinitionPage.builder()
          .definitions(List.of(def)).totalCount(1).build();

      when(eventDefinitionDao.queryEventDefinitions(isNull(), isNull(), eq(50), eq(0)))
          .thenReturn(Single.just(page));
      when(eventDefinitionDao.getAttributesForEvents(List.of(1L)))
          .thenReturn(Single.just(Collections.emptyMap()));

      EventDefinitionPage result = service.queryEventDefinitions(null, null, 50, 0)
          .blockingGet();

      assertThat(result.getDefinitions().get(0).getAttributes()).isEmpty();
    }

    @Test
    void shouldWrapQueryDaoErrorAsWebException() {
      when(eventDefinitionDao.queryEventDefinitions(isNull(), isNull(), eq(50), eq(0)))
          .thenReturn(Single.error(new RuntimeException("Connection refused")));

      service.queryEventDefinitions(null, null, 50, 0)
          .test()
          .assertError(WebApplicationException.class);
    }
  }

  @Nested
  class TestGetDistinctCategories {

    @Test
    void shouldReturnDistinctCategories() {
      when(eventDefinitionDao.getDistinctCategories())
          .thenReturn(Single.just(List.of("commerce", "auth", "navigation")));

      List<String> result = service.getDistinctCategories().blockingGet();

      assertThat(result).containsExactly("commerce", "auth", "navigation");
    }

    @Test
    void shouldReturnEmptyListWhenNoCategories() {
      when(eventDefinitionDao.getDistinctCategories())
          .thenReturn(Single.just(Collections.emptyList()));

      List<String> result = service.getDistinctCategories().blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldWrapCategoryDaoErrorAsWebException() {
      when(eventDefinitionDao.getDistinctCategories())
          .thenReturn(Single.error(new RuntimeException("DB read error")));

      service.getDistinctCategories()
          .test()
          .assertError(WebApplicationException.class);
    }
  }

  @Nested
  class TestArchiveEventDefinition {

    @Test
    void shouldArchiveEventDefinition() {
      when(eventDefinitionDao.archiveEventDefinition(1L, TEST_USER))
          .thenReturn(Completable.complete());

      service.archiveEventDefinition(1L, TEST_USER).blockingAwait();

      verify(eventDefinitionDao).archiveEventDefinition(1L, TEST_USER);
    }

    @Test
    void shouldWrapArchiveDaoErrorAsWebException() {
      when(eventDefinitionDao.archiveEventDefinition(1L, TEST_USER))
          .thenReturn(Completable.error(new RuntimeException("DB failure")));

      service.archiveEventDefinition(1L, TEST_USER)
          .test()
          .assertError(WebApplicationException.class);
    }
  }

  @Nested
  class TestBulkUpload {

    @Test
    void shouldParseCsvAndUpsert() {
      String csv = CSV_HEADER
          + "cart_viewed,User viewed cart,commerce,itemCount,Number of items,int,true\n"
          + "cart_viewed,User viewed cart,commerce,cartValue,Total value,double,false\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));

      BulkUploadResult result = service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .blockingGet();

      assertThat(result.getCreated()).isEqualTo(1);
      assertThat(result.getUpdated()).isEqualTo(0);
      assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldReportErrorsForBlankEventNames() {
      String csv = CSV_HEADER
          + ",Missing name,commerce,attr1,desc,string,false\n"
          + "valid_event,Valid,commerce,attr1,desc,string,false\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));

      BulkUploadResult result = service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .blockingGet();

      assertThat(result.getErrors()).hasSize(1);
      assertThat(result.getErrors().get(0).getLine()).isEqualTo(2);
      assertThat(result.getErrors().get(0).getMessage()).contains("blank");
      assertThat(result.getCreated()).isEqualTo(1);
    }

    @Test
    void shouldHandleEventsWithNoAttributes() {
      String csv = CSV_HEADER
          + "login_success,User logged in,auth,,,,\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));

      BulkUploadResult result = service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .blockingGet();

      assertThat(result.getCreated()).isEqualTo(1);
      assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldCountUpdatedEventsWhenUpsertReturnsNegativeId() {
      String csv = CSV_HEADER
          + "existing_event,Updated desc,commerce,attr1,desc,string,false\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(-5L));

      BulkUploadResult result = service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .blockingGet();

      assertThat(result.getCreated()).isEqualTo(0);
      assertThat(result.getUpdated()).isEqualTo(1);
      assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldHandleMultipleEventsWithMixedCreateAndUpdate() {
      String csv = CSV_HEADER
          + "new_event,New,commerce,,,string,false\n"
          + "existing_event,Existing,auth,,,string,false\n"
          + "another_new,Another new,nav,,,string,false\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L))
          .thenReturn(Single.just(-2L))
          .thenReturn(Single.just(3L));

      BulkUploadResult result = service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .blockingGet();

      assertThat(result.getCreated()).isEqualTo(2);
      assertThat(result.getUpdated()).isEqualTo(1);
      assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldGroupMultipleRowsIntoSingleEvent() {
      String csv = CSV_HEADER
          + "checkout,Checkout flow,commerce,total,Total amount,double,true\n"
          + "checkout,Checkout flow,commerce,currency,Currency code,string,true\n"
          + "checkout,Checkout flow,commerce,items,Item count,int,false\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));

      BulkUploadResult result = service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .blockingGet();

      assertThat(result.getCreated()).isEqualTo(1);

      ArgumentCaptor<EventDefinition> captor =
          ArgumentCaptor.forClass(EventDefinition.class);
      verify(eventDefinitionDao).upsertEventDefinition(captor.capture());

      EventDefinition captured = captor.getValue();
      assertThat(captured.getEventName()).isEqualTo("checkout");
      assertThat(captured.getAttributes()).hasSize(3);
    }

    @Test
    void shouldHandleEmptyCsv() {
      String csv = CSV_HEADER;

      BulkUploadResult result = service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .blockingGet();

      assertThat(result.getCreated()).isEqualTo(0);
      assertThat(result.getUpdated()).isEqualTo(0);
      assertThat(result.getErrors()).isEmpty();
      verify(eventDefinitionDao, never()).upsertEventDefinition(any());
    }

    @Test
    void shouldSkipBlankLines() {
      String csv = CSV_HEADER
          + "\n"
          + "valid_event,Desc,cat,attr,desc,string,false\n"
          + "\n"
          + "\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));

      BulkUploadResult result = service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .blockingGet();

      assertThat(result.getCreated()).isEqualTo(1);
      assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldRejectInvalidHeaders() {
      String csv = "wrong_col1,wrong_col2,wrong_col3\n"
          + "data1,data2,data3\n";

      service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .test()
          .assertError(err -> {
            assertThat(err).isInstanceOf(WebApplicationException.class);
            WebApplicationException wae = (WebApplicationException) err;
            assertThat(wae.getResponse().getStatus()).isEqualTo(400);
            return true;
          });
    }

    @Test
    void shouldRejectCsvWithMissingHeaderColumns() {
      String csv = "event_name,event_description\n"
          + "test_event,Some desc\n";

      service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .test()
          .assertError(err -> {
            assertThat(err).isInstanceOf(WebApplicationException.class);
            WebApplicationException wae = (WebApplicationException) err;
            assertThat(wae.getResponse().getStatus()).isEqualTo(400);
            return true;
          });
    }

    @Test
    void shouldHandleCsvWithBom() {
      String csv = "\uFEFF" + CSV_HEADER
          + "bom_event,BOM test,misc,attr1,desc,string,false\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));

      BulkUploadResult result = service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .blockingGet();

      assertThat(result.getCreated()).isEqualTo(1);
      assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldHandleQuotedFieldsWithCommas() {
      String csv = CSV_HEADER
          + "test_event,\"Description, with comma\",commerce,attr1,\"Attr desc, also comma\",string,false\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));

      BulkUploadResult result = service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .blockingGet();

      assertThat(result.getCreated()).isEqualTo(1);

      ArgumentCaptor<EventDefinition> captor =
          ArgumentCaptor.forClass(EventDefinition.class);
      verify(eventDefinitionDao).upsertEventDefinition(captor.capture());

      EventDefinition captured = captor.getValue();
      assertThat(captured.getDescription()).isEqualTo("Description, with comma");
    }

    @Test
    void shouldHandleEscapedDoubleQuotesInCsv() {
      String csv = CSV_HEADER
          + "test_event,\"She said \"\"hello\"\"\",commerce,attr1,desc,string,false\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));

      BulkUploadResult result = service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .blockingGet();

      assertThat(result.getCreated()).isEqualTo(1);

      ArgumentCaptor<EventDefinition> captor =
          ArgumentCaptor.forClass(EventDefinition.class);
      verify(eventDefinitionDao).upsertEventDefinition(captor.capture());
      assertThat(captor.getValue().getDescription()).isEqualTo("She said \"hello\"");
    }

    @Test
    void shouldDefaultAttributeTypeToString() {
      String csv = CSV_HEADER
          + "test_event,Desc,cat,my_attr,Attr desc,,false\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));

      service.bulkUploadFromCsv(csvStream(csv), TEST_USER).blockingGet();

      ArgumentCaptor<EventDefinition> captor =
          ArgumentCaptor.forClass(EventDefinition.class);
      verify(eventDefinitionDao).upsertEventDefinition(captor.capture());

      EventDefinition captured = captor.getValue();
      assertThat(captured.getAttributes()).hasSize(1);
      assertThat(captured.getAttributes().get(0).getDataType()).isEqualTo("string");
    }

    @Test
    void shouldParseAttributeRequiredField() {
      String csv = CSV_HEADER
          + "test_event,Desc,cat,req_attr,Desc,string,true\n"
          + "test_event,Desc,cat,opt_attr,Desc,string,false\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));

      service.bulkUploadFromCsv(csvStream(csv), TEST_USER).blockingGet();

      ArgumentCaptor<EventDefinition> captor =
          ArgumentCaptor.forClass(EventDefinition.class);
      verify(eventDefinitionDao).upsertEventDefinition(captor.capture());

      EventDefinition captured = captor.getValue();
      assertThat(captured.getAttributes().get(0).isRequired()).isTrue();
      assertThat(captured.getAttributes().get(1).isRequired()).isFalse();
    }

    @Test
    void shouldContinueProcessingAfterUpsertError() {
      String csv = CSV_HEADER
          + "failing_event,Desc,cat,attr,desc,string,false\n"
          + "success_event,Desc,cat,attr,desc,string,false\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.error(
              new RuntimeException("errorCode=1062: Duplicate entry for key 'uk_project_event'")))
          .thenReturn(Single.just(1L));

      BulkUploadResult result = service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .blockingGet();

      assertThat(result.getCreated()).isEqualTo(1);
      assertThat(result.getErrors()).hasSize(1);
      assertThat(result.getErrors().get(0).getEventName()).isEqualTo("failing_event");
    }

    @Test
    void shouldHandleAllRowsWithBlankEventNames() {
      String csv = CSV_HEADER
          + ",,cat1,attr1,desc,string,false\n"
          + ",,cat2,attr2,desc,string,false\n";

      BulkUploadResult result = service.bulkUploadFromCsv(csvStream(csv), TEST_USER)
          .blockingGet();

      assertThat(result.getCreated()).isEqualTo(0);
      assertThat(result.getUpdated()).isEqualTo(0);
      assertThat(result.getErrors()).hasSize(2);
      verify(eventDefinitionDao, never()).upsertEventDefinition(any());
    }

    @Test
    void shouldReturnEmptyResultForHeaderOnlyCsv() {
      BulkUploadResult result = service.bulkUploadFromCsv(
          csvStream(CSV_HEADER), TEST_USER
      ).blockingGet();

      assertThat(result.getCreated()).isEqualTo(0);
      assertThat(result.getUpdated()).isEqualTo(0);
      assertThat(result.getSkipped()).isEqualTo(0);
      assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldSetCreatedByOnUpsertedEvent() {
      String csv = CSV_HEADER
          + "test_event,Desc,cat,,,string,false\n";

      when(eventDefinitionDao.upsertEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.just(1L));

      service.bulkUploadFromCsv(csvStream(csv), "uploader@test.com").blockingGet();

      ArgumentCaptor<EventDefinition> captor =
          ArgumentCaptor.forClass(EventDefinition.class);
      verify(eventDefinitionDao).upsertEventDefinition(captor.capture());
      assertThat(captor.getValue().getCreatedBy()).isEqualTo("uploader@test.com");
    }
  }

  @Nested
  class TestErrorTranslation {

    @Test
    void shouldTranslateDuplicateAttributeError() {
      when(eventDefinitionDao.createEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.error(
              new RuntimeException("errorCode=1062: Duplicate entry for key 'uk_event_attr'")));

      service.createEventDefinition(CreateEventDefinitionRequest.builder()
              .eventName("test").user(TEST_USER).build())
          .test()
          .assertError(err -> {
            assertThat(err).isInstanceOf(WebApplicationException.class);
            WebApplicationException wae = (WebApplicationException) err;
            assertThat(wae.getResponse().getStatus()).isEqualTo(409);
            return true;
          });
    }

    @Test
    void shouldTranslateDataTooLongError() {
      when(eventDefinitionDao.createEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.error(
              new RuntimeException("Data too long for column 'event_name'")));

      service.createEventDefinition(CreateEventDefinitionRequest.builder()
              .eventName("test").user(TEST_USER).build())
          .test()
          .assertError(err -> {
            assertThat(err).isInstanceOf(WebApplicationException.class);
            WebApplicationException wae = (WebApplicationException) err;
            assertThat(wae.getResponse().getStatus()).isEqualTo(400);
            return true;
          });
    }

    @Test
    void shouldTranslateForeignKeyError() {
      when(eventDefinitionDao.createEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.error(
              new RuntimeException("Cannot add or update a child row: a foreign key constraint fails")));

      service.createEventDefinition(CreateEventDefinitionRequest.builder()
              .eventName("test").user(TEST_USER).build())
          .test()
          .assertError(err -> {
            assertThat(err).isInstanceOf(WebApplicationException.class);
            WebApplicationException wae = (WebApplicationException) err;
            assertThat(wae.getResponse().getStatus()).isEqualTo(400);
            return true;
          });
    }

    @Test
    void shouldTranslateConnectionError() {
      when(eventDefinitionDao.createEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.error(new RuntimeException("Connection refused")));

      service.createEventDefinition(CreateEventDefinitionRequest.builder()
              .eventName("test").user(TEST_USER).build())
          .test()
          .assertError(err -> {
            assertThat(err).isInstanceOf(WebApplicationException.class);
            WebApplicationException wae = (WebApplicationException) err;
            assertThat(wae.getResponse().getStatus()).isEqualTo(400);
            return true;
          });
    }

    @Test
    void shouldTranslateTimeoutError() {
      when(eventDefinitionDao.createEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.error(new RuntimeException("Timeout waiting for connection")));

      service.createEventDefinition(CreateEventDefinitionRequest.builder()
              .eventName("test").user(TEST_USER).build())
          .test()
          .assertError(err -> {
            assertThat(err).isInstanceOf(WebApplicationException.class);
            WebApplicationException wae = (WebApplicationException) err;
            assertThat(wae.getResponse().getStatus()).isEqualTo(400);
            return true;
          });
    }

    @Test
    void shouldFallbackToGenericErrorForUnknownExceptions() {
      when(eventDefinitionDao.createEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.error(new RuntimeException("Something totally unexpected")));

      service.createEventDefinition(CreateEventDefinitionRequest.builder()
              .eventName("test").user(TEST_USER).build())
          .test()
          .assertError(err -> {
            assertThat(err).isInstanceOf(WebApplicationException.class);
            WebApplicationException wae = (WebApplicationException) err;
            assertThat(wae.getResponse().getStatus()).isEqualTo(500);
            return true;
          });
    }

    @Test
    void shouldHandleNullMessageError() {
      when(eventDefinitionDao.createEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.error(new RuntimeException((String) null)));

      service.createEventDefinition(CreateEventDefinitionRequest.builder()
              .eventName("test").user(TEST_USER).build())
          .test()
          .assertError(err -> {
            assertThat(err).isInstanceOf(WebApplicationException.class);
            WebApplicationException wae = (WebApplicationException) err;
            assertThat(wae.getResponse().getStatus()).isEqualTo(500);
            return true;
          });
    }

    @Test
    void shouldPassThroughExistingWebApplicationException() {
      WebApplicationException existing = new WebApplicationException("Already a WAE", 422);
      when(eventDefinitionDao.createEventDefinition(any(EventDefinition.class)))
          .thenReturn(Single.error(existing));

      service.createEventDefinition(CreateEventDefinitionRequest.builder()
              .eventName("test").user(TEST_USER).build())
          .test()
          .assertError(err -> {
            assertThat(err).isSameAs(existing);
            return true;
          });
    }

    @Test
    void shouldTranslateNotFoundExceptionTo404() {
      when(eventDefinitionDao.getEventDefinitionById(42L))
          .thenReturn(Single.error(new EventDefinitionNotFoundException(42L)));

      service.getEventDefinitionById(42L)
          .test()
          .assertError(err -> {
            assertThat(err).isInstanceOf(WebApplicationException.class);
            WebApplicationException wae = (WebApplicationException) err;
            assertThat(wae.getResponse().getStatus()).isEqualTo(404);
            return true;
          });
    }

    @Test
    void shouldTranslateNotFoundByNameTo404() {
      when(eventDefinitionDao.getEventDefinitionById(99L))
          .thenReturn(Single.error(new EventDefinitionNotFoundException("missing_event")));

      service.getEventDefinitionById(99L)
          .test()
          .assertError(err -> {
            assertThat(err).isInstanceOf(WebApplicationException.class);
            WebApplicationException wae = (WebApplicationException) err;
            assertThat(wae.getResponse().getStatus()).isEqualTo(404);
            return true;
          });
    }
  }
}
