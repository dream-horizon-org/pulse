package org.dreamhorizon.pulseserver.resources.eventdefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import jakarta.ws.rs.WebApplicationException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.RestEventAttribute;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.RestEventDefinition;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.RestEventDefinitionListResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.eventdefinition.EventDefinitionService;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
class EventDefinitionControllerTest {

  private static final String TEST_USER = "test@pulse.dev";

  @Mock
  private EventDefinitionService eventDefinitionService;

  private EventDefinitionController controller;

  @BeforeEach
  void setup() {
    controller = new EventDefinitionController(eventDefinitionService);
    TenantContext.setTenantId("default");
  }

  private EventDefinition buildEventDefinition(Long id, String name) {
    return EventDefinition.builder()
        .id(id)
        .eventName(name)
        .displayName(name)
        .description("Desc for " + name)
        .category("test")
        .archived(false)
        .createdBy(TEST_USER)
        .createdAt(Timestamp.valueOf(LocalDateTime.now()))
        .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
        .attributes(Collections.emptyList())
        .build();
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetAllEventDefinitions {

    @Test
    void shouldReturnPaginatedList(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinitionPage page = EventDefinitionPage.builder()
            .definitions(List.of(
                buildEventDefinition(1L, "event_a"),
                buildEventDefinition(2L, "event_b")))
            .totalCount(2)
            .build();

        when(eventDefinitionService.queryEventDefinitions(null, null, 50, 0))
            .thenReturn(Single.just(page));

        CompletionStage<Response<RestEventDefinitionListResponse>> result =
            controller.getAllEventDefinitions(50, 0, null, null);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertThat(resp.getData().getEventDefinitions()).hasSize(2);
            assertThat(resp.getData().getTotalCount()).isEqualTo(2);
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldPassSearchAndCategoryParams(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinitionPage page = EventDefinitionPage.builder()
            .definitions(List.of(buildEventDefinition(1L, "cart_viewed")))
            .totalCount(1)
            .build();

        when(eventDefinitionService.queryEventDefinitions("cart", "commerce", 20, 10))
            .thenReturn(Single.just(page));

        CompletionStage<Response<RestEventDefinitionListResponse>> result =
            controller.getAllEventDefinitions(20, 10, "cart", "commerce");

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertThat(resp.getData().getEventDefinitions()).hasSize(1);
            assertThat(resp.getData().getEventDefinitions().get(0).getEventName())
                .isEqualTo("cart_viewed");
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldReturnEmptyListWhenNoResults(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinitionPage page = EventDefinitionPage.builder()
            .definitions(Collections.emptyList())
            .totalCount(0)
            .build();

        when(eventDefinitionService.queryEventDefinitions(null, null, 50, 0))
            .thenReturn(Single.just(page));

        CompletionStage<Response<RestEventDefinitionListResponse>> result =
            controller.getAllEventDefinitions(50, 0, null, null);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertThat(resp.getData().getEventDefinitions()).isEmpty();
            assertThat(resp.getData().getTotalCount()).isEqualTo(0);
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldPropagateServiceError(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        when(eventDefinitionService.queryEventDefinitions(null, null, 50, 0))
            .thenReturn(Single.error(
                ServiceError.DATABASE_ERROR.getCustomException("DB error", "DB error", 500)));

        CompletionStage<Response<RestEventDefinitionListResponse>> result =
            controller.getAllEventDefinitions(50, 0, null, null);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
            assertThat(((WebApplicationException) err).getResponse().getStatus()).isEqualTo(500);
          });
          ctx.completeNow();
        });
      });
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetEventDefinitionById {

    @Test
    void shouldReturnEventDefinitionWithAttributes(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition def = buildEventDefinition(1L, "checkout")
            .toBuilder()
            .attributes(List.of(
                EventAttributeDefinition.builder()
                    .id(10L)
                    .attributeName("total")
                    .dataType("double")
                    .required(true)
                    .build()))
            .build();

        when(eventDefinitionService.getEventDefinitionById(1L))
            .thenReturn(Single.just(def));

        CompletionStage<Response<RestEventDefinition>> result =
            controller.getEventDefinitionById(1L);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertThat(resp.getData().getEventName()).isEqualTo("checkout");
            assertThat(resp.getData().getAttributes()).hasSize(1);
            assertThat(resp.getData().getAttributes().get(0).getAttributeName())
                .isEqualTo("total");
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldReturn404WhenNotFound(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        when(eventDefinitionService.getEventDefinitionById(999L))
            .thenReturn(Single.error(
                ServiceError.NOT_FOUND.getCustomException("Not found", "Not found", 404)));

        CompletionStage<Response<RestEventDefinition>> result =
            controller.getEventDefinitionById(999L);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
            assertThat(((WebApplicationException) err).getResponse().getStatus()).isEqualTo(404);
          });
          ctx.completeNow();
        });
      });
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestCreateEventDefinition {

    @Test
    void shouldCreateAndReturnEventDefinition(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition created = buildEventDefinition(1L, "new_event");

        when(eventDefinitionService.createEventDefinition(any(CreateEventDefinitionRequest.class)))
            .thenReturn(Single.just(created));

        RestEventDefinition request = new RestEventDefinition();
        request.setEventName("new_event");
        request.setDescription("A new event");
        request.setCategory("test");
        request.setAttributes(List.of());

        CompletionStage<Response<RestEventDefinition>> result =
            controller.createEventDefinition(TEST_USER, request);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertThat(resp.getData().getEventName()).isEqualTo("new_event");
            assertThat(resp.getData().getId()).isEqualTo(1L);
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldPassUserEmailToService(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition created = buildEventDefinition(1L, "test_event");
        when(eventDefinitionService.createEventDefinition(any(CreateEventDefinitionRequest.class)))
            .thenReturn(Single.just(created));

        RestEventDefinition request = new RestEventDefinition();
        request.setEventName("test_event");

        controller.createEventDefinition("creator@test.com", request);

        ArgumentCaptor<CreateEventDefinitionRequest> captor =
            ArgumentCaptor.forClass(CreateEventDefinitionRequest.class);

        // Allow async completion then verify
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        verify(eventDefinitionService).createEventDefinition(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo("creator@test.com");
        ctx.completeNow();
      });
    }

    @Test
    void shouldMapRestAttributesToServiceAttributes(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition created = buildEventDefinition(1L, "test_event");
        when(eventDefinitionService.createEventDefinition(any(CreateEventDefinitionRequest.class)))
            .thenReturn(Single.just(created));

        RestEventAttribute restAttr = new RestEventAttribute();
        restAttr.setAttributeName("price");
        restAttr.setDescription("Item price");
        restAttr.setDataType("double");
        restAttr.setIsRequired(true);

        RestEventDefinition request = new RestEventDefinition();
        request.setEventName("test_event");
        request.setAttributes(List.of(restAttr));

        controller.createEventDefinition(TEST_USER, request);

        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        ArgumentCaptor<CreateEventDefinitionRequest> captor =
            ArgumentCaptor.forClass(CreateEventDefinitionRequest.class);
        verify(eventDefinitionService).createEventDefinition(captor.capture());

        ctx.verify(() -> {
          List<EventAttributeDefinition> attrs = captor.getValue().getAttributes();
          assertThat(attrs).hasSize(1);
          assertThat(attrs.get(0).getAttributeName()).isEqualTo("price");
          assertThat(attrs.get(0).getDataType()).isEqualTo("double");
          assertThat(attrs.get(0).isRequired()).isTrue();
        });
        ctx.completeNow();
      });
    }

    @Test
    void shouldHandleNullAttributesList(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition created = buildEventDefinition(1L, "test_event");
        when(eventDefinitionService.createEventDefinition(any(CreateEventDefinitionRequest.class)))
            .thenReturn(Single.just(created));

        RestEventDefinition request = new RestEventDefinition();
        request.setEventName("test_event");
        request.setAttributes(null);

        controller.createEventDefinition(TEST_USER, request);

        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        ArgumentCaptor<CreateEventDefinitionRequest> captor =
            ArgumentCaptor.forClass(CreateEventDefinitionRequest.class);
        verify(eventDefinitionService).createEventDefinition(captor.capture());

        ctx.verify(() -> assertThat(captor.getValue().getAttributes()).isNull());
        ctx.completeNow();
      });
    }

    @Test
    void shouldReturn409OnDuplicateError(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        when(eventDefinitionService.createEventDefinition(any(CreateEventDefinitionRequest.class)))
            .thenReturn(Single.error(
                ServiceError.INTERNAL_SERVER_ERROR.getCustomException("Duplicate", "Duplicate", 409)));

        RestEventDefinition request = new RestEventDefinition();
        request.setEventName("existing_event");

        CompletionStage<Response<RestEventDefinition>> result =
            controller.createEventDefinition(TEST_USER, request);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
            assertThat(((WebApplicationException) err).getResponse().getStatus()).isEqualTo(409);
          });
          ctx.completeNow();
        });
      });
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestUpdateEventDefinition {

    @Test
    void shouldUpdateAndReturnDefinition(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition updated = buildEventDefinition(1L, "updated_event")
            .toBuilder().description("Updated").build();

        when(eventDefinitionService.updateEventDefinition(any(UpdateEventDefinitionRequest.class)))
            .thenReturn(Completable.complete());
        when(eventDefinitionService.getEventDefinitionById(1L))
            .thenReturn(Single.just(updated));

        RestEventDefinition request = new RestEventDefinition();
        request.setDescription("Updated");

        CompletionStage<Response<RestEventDefinition>> result =
            controller.updateEventDefinition(TEST_USER, 1L, request);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertThat(resp.getData().getId()).isEqualTo(1L);
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldPassPathIdToServiceRequest(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition updated = buildEventDefinition(5L, "test_event");
        when(eventDefinitionService.updateEventDefinition(any(UpdateEventDefinitionRequest.class)))
            .thenReturn(Completable.complete());
        when(eventDefinitionService.getEventDefinitionById(5L))
            .thenReturn(Single.just(updated));

        RestEventDefinition request = new RestEventDefinition();
        request.setDescription("Updated");

        controller.updateEventDefinition(TEST_USER, 5L, request);

        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        ArgumentCaptor<UpdateEventDefinitionRequest> captor =
            ArgumentCaptor.forClass(UpdateEventDefinitionRequest.class);
        verify(eventDefinitionService).updateEventDefinition(captor.capture());

        ctx.verify(() -> {
          assertThat(captor.getValue().getId()).isEqualTo(5L);
          assertThat(captor.getValue().getUser()).isEqualTo(TEST_USER);
        });
        ctx.completeNow();
      });
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestArchiveEventDefinition {

    @Test
    void shouldArchiveAndReturnDefinition(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition archived = buildEventDefinition(1L, "old_event")
            .toBuilder().archived(true).build();

        when(eventDefinitionService.archiveEventDefinition(eq(1L), eq(TEST_USER)))
            .thenReturn(Completable.complete());
        when(eventDefinitionService.getEventDefinitionById(1L))
            .thenReturn(Single.just(archived));

        CompletionStage<Response<RestEventDefinition>> result =
            controller.archiveEventDefinition(TEST_USER, 1L);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertThat(resp.getData().getIsArchived()).isTrue();
            verify(eventDefinitionService).archiveEventDefinition(1L, TEST_USER);
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldPropagateArchiveError(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        when(eventDefinitionService.archiveEventDefinition(eq(1L), eq(TEST_USER)))
            .thenReturn(Completable.error(
                ServiceError.NOT_FOUND.getCustomException("Not found", "Not found", 404)));

        CompletionStage<Response<RestEventDefinition>> result =
            controller.archiveEventDefinition(TEST_USER, 1L);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
          });
          ctx.completeNow();
        });
      });
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetCategories {

    @Test
    void shouldReturnDistinctCategories(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        when(eventDefinitionService.getDistinctCategories())
            .thenReturn(Single.just(List.of("commerce", "auth", "navigation")));

        CompletionStage<Response<List<String>>> result = controller.getCategories();

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertThat(resp.getData()).containsExactly("commerce", "auth", "navigation");
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldHandleServiceError(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        when(eventDefinitionService.getDistinctCategories())
            .thenReturn(Single.error(
                ServiceError.DATABASE_ERROR.getCustomException("DB err", "DB err", 500)));

        CompletionStage<Response<List<String>>> result = controller.getCategories();

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
          });
          ctx.completeNow();
        });
      });
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestResponseMapping {

    @Test
    void shouldMapAllFieldsToRestModel(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        EventDefinition def = EventDefinition.builder()
            .id(1L)
            .eventName("full_event")
            .displayName("Full Event")
            .description("Full description")
            .category("commerce")
            .archived(false)
            .createdBy("creator@test.com")
            .updatedBy("updater@test.com")
            .createdAt(now)
            .updatedAt(now)
            .attributes(List.of(
                EventAttributeDefinition.builder()
                    .id(10L)
                    .attributeName("price")
                    .description("Item price")
                    .dataType("double")
                    .required(true)
                    .archived(false)
                    .build()))
            .build();

        when(eventDefinitionService.getEventDefinitionById(1L))
            .thenReturn(Single.just(def));

        CompletionStage<Response<RestEventDefinition>> result =
            controller.getEventDefinitionById(1L);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            RestEventDefinition rest = resp.getData();
            assertThat(rest.getId()).isEqualTo(1L);
            assertThat(rest.getEventName()).isEqualTo("full_event");
            assertThat(rest.getDisplayName()).isEqualTo("Full Event");
            assertThat(rest.getDescription()).isEqualTo("Full description");
            assertThat(rest.getCategory()).isEqualTo("commerce");
            assertThat(rest.getIsArchived()).isFalse();
            assertThat(rest.getCreatedBy()).isEqualTo("creator@test.com");
            assertThat(rest.getUpdatedBy()).isEqualTo("updater@test.com");
            assertThat(rest.getCreatedAt()).isEqualTo(now);
            assertThat(rest.getUpdatedAt()).isEqualTo(now);
            assertThat(rest.getAttributes()).hasSize(1);

            RestEventAttribute restAttr = rest.getAttributes().get(0);
            assertThat(restAttr.getId()).isEqualTo(10L);
            assertThat(restAttr.getAttributeName()).isEqualTo("price");
            assertThat(restAttr.getDataType()).isEqualTo("double");
            assertThat(restAttr.getIsRequired()).isTrue();
            assertThat(restAttr.getIsArchived()).isFalse();
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldMapNullAttributesToEmptyList(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition def = buildEventDefinition(1L, "no_attrs")
            .toBuilder().attributes(null).build();

        when(eventDefinitionService.getEventDefinitionById(1L))
            .thenReturn(Single.just(def));

        CompletionStage<Response<RestEventDefinition>> result =
            controller.getEventDefinitionById(1L);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertThat(resp.getData().getAttributes()).isEmpty();
          });
          ctx.completeNow();
        });
      });
    }
  }
}
