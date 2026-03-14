package org.dreamhorizon.pulseserver.resources.eventdefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.EventSearchResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.eventdefinition.EventDefinitionService;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventAttributeDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinitionPage;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
class EventSearchControllerTest {

  @Mock
  private EventDefinitionService eventDefinitionService;

  private EventSearchController controller;

  @BeforeEach
  void setup() {
    controller = new EventSearchController(eventDefinitionService);
    TenantContext.setTenantId("default");
  }

  private EventDefinition buildDef(Long id, String name, String desc) {
    return EventDefinition.builder()
        .id(id)
        .eventName(name)
        .description(desc)
        .archived(false)
        .createdBy("user@test.com")
        .createdAt(Timestamp.valueOf(LocalDateTime.now()))
        .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
        .attributes(Collections.emptyList())
        .build();
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestSearchEvents {

    @Test
    void shouldReturnEmptyListForBlankSearch(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        CompletionStage<Response<EventSearchResponse>> result =
            controller.searchEvents("", 10);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertThat(resp.getData().getEventList()).isEmpty();
            assertThat(resp.getData().getRecordCount()).isEqualTo(0);
            verify(eventDefinitionService, never())
                .queryEventDefinitions(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt());
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldReturnEmptyListForWhitespaceSearch(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        CompletionStage<Response<EventSearchResponse>> result =
            controller.searchEvents("   ", 10);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertThat(resp.getData().getEventList()).isEmpty();
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldSearchAndReturnMappedResults(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition def = buildDef(1L, "user_login", "User login event")
            .toBuilder()
            .attributes(List.of(
                EventAttributeDefinition.builder()
                    .attributeName("method")
                    .description("Login method")
                    .archived(false)
                    .build()))
            .build();

        EventDefinitionPage page = EventDefinitionPage.builder()
            .definitions(List.of(def))
            .totalCount(1)
            .build();

        when(eventDefinitionService.queryEventDefinitions("user", null, 10, 0))
            .thenReturn(Single.just(page));

        CompletionStage<Response<EventSearchResponse>> result =
            controller.searchEvents("user", 10);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            EventSearchResponse data = resp.getData();
            assertThat(data.getRecordCount()).isEqualTo(1);
            assertThat(data.getEventList()).hasSize(1);

            EventSearchResponse.EventSearchItem item = data.getEventList().get(0);
            assertThat(item.getMetadata().getEventName()).isEqualTo("user_login");
            assertThat(item.getMetadata().getDescription()).isEqualTo("User login event");
            assertThat(item.getMetadata().getScreenNames()).isEmpty();
            assertThat(item.getMetadata().isActive()).isTrue();
            assertThat(item.getProperties()).hasSize(1);
            assertThat(item.getProperties().get(0).getPropertyName()).isEqualTo("method");
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldPassNullCategoryToService(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinitionPage page = EventDefinitionPage.builder()
            .definitions(Collections.emptyList())
            .totalCount(0)
            .build();

        when(eventDefinitionService.queryEventDefinitions("test", null, 5, 0))
            .thenReturn(Single.just(page));

        CompletionStage<Response<EventSearchResponse>> result =
            controller.searchEvents("test", 5);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            verify(eventDefinitionService).queryEventDefinitions("test", null, 5, 0);
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldHandleNullDescriptionInMapping(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition def = buildDef(1L, "bare_event", null);

        EventDefinitionPage page = EventDefinitionPage.builder()
            .definitions(List.of(def))
            .totalCount(1)
            .build();

        when(eventDefinitionService.queryEventDefinitions("bare", null, 10, 0))
            .thenReturn(Single.just(page));

        CompletionStage<Response<EventSearchResponse>> result =
            controller.searchEvents("bare", 10);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertThat(resp.getData().getEventList().get(0).getMetadata().getDescription())
                .isEqualTo("");
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldHandleNullAttributesInMapping(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition def = buildDef(1L, "no_attrs", "desc")
            .toBuilder().attributes(null).build();

        EventDefinitionPage page = EventDefinitionPage.builder()
            .definitions(List.of(def))
            .totalCount(1)
            .build();

        when(eventDefinitionService.queryEventDefinitions("no", null, 10, 0))
            .thenReturn(Single.just(page));

        CompletionStage<Response<EventSearchResponse>> result =
            controller.searchEvents("no", 10);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertThat(resp.getData().getEventList().get(0).getProperties()).isEmpty();
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldHandleNullAttributeDescription(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition def = buildDef(1L, "test", "desc")
            .toBuilder()
            .attributes(List.of(
                EventAttributeDefinition.builder()
                    .attributeName("attr1")
                    .description(null)
                    .archived(false)
                    .build()))
            .build();

        EventDefinitionPage page = EventDefinitionPage.builder()
            .definitions(List.of(def))
            .totalCount(1)
            .build();

        when(eventDefinitionService.queryEventDefinitions("test", null, 10, 0))
            .thenReturn(Single.just(page));

        CompletionStage<Response<EventSearchResponse>> result =
            controller.searchEvents("test", 10);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            assertThat(resp.getData().getEventList().get(0).getProperties().get(0)
                .getDescription()).isEqualTo("");
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldMapArchivedEventCorrectly(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        EventDefinition def = buildDef(1L, "old_event", "desc")
            .toBuilder().archived(true).build();

        EventDefinitionPage page = EventDefinitionPage.builder()
            .definitions(List.of(def))
            .totalCount(1)
            .build();

        when(eventDefinitionService.queryEventDefinitions("old", null, 10, 0))
            .thenReturn(Single.just(page));

        CompletionStage<Response<EventSearchResponse>> result =
            controller.searchEvents("old", 10);

        result.whenComplete((resp, err) -> {
          ctx.verify(() -> {
            assertNull(err);
            EventSearchResponse.EventMetadata meta =
                resp.getData().getEventList().get(0).getMetadata();
            assertThat(meta.isArchived()).isTrue();
            assertThat(meta.isActive()).isFalse();
          });
          ctx.completeNow();
        });
      });
    }

    @Test
    void shouldPropagateServiceError(Vertx vertx, VertxTestContext ctx) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId("default");

        when(eventDefinitionService.queryEventDefinitions("error", null, 10, 0))
            .thenReturn(Single.error(
                ServiceError.DATABASE_ERROR.getCustomException("DB error", "DB error", 500)));

        CompletionStage<Response<EventSearchResponse>> result =
            controller.searchEvents("error", 10);

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
}
