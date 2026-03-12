package org.dreamhorizon.pulseserver.resources.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Base64;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.constant.NotificationConstants;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.resources.notification.models.ContactRequestDto;
import org.dreamhorizon.pulseserver.resources.notification.models.NotificationBatchResponseDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SendNotificationRequestDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationControllerTest {

  private static final String TENANT_ID = "tenant-123";
  private static final String USER_EMAIL = "user@example.com";

  @Mock NotificationService notificationService;
  @Mock TenantService tenantService;

  NotificationController controller;

  private String buildJwt(String email) {
    String header = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"alg\":\"none\"}".getBytes());
    String payload = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(("{\"email\":\"" + email + "\"}").getBytes());
    return header + "." + payload + ".sig";
  }

  @BeforeEach
  void setUp() {
    controller = new NotificationController(notificationService, tenantService);
    TenantContext.setTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  class ContactUs {

    @Test
    void shouldReturnErrorForInvalidContactType(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId(TENANT_ID);
        String auth = "Bearer " + buildJwt(USER_EMAIL);

        CompletionStage<Response<String>> result =
            controller.contactUs(auth, "invalid", null);

        result.whenComplete((response, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNull();
            assertThat(response.getError()).isNotNull();
            assertThat(response.getError().getCode()).isEqualTo("INVALID_TYPE");
            assertThat(response.getError().getMessage())
                .contains("Invalid contact type");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldReturnErrorForNullContactType(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId(TENANT_ID);
        String auth = "Bearer " + buildJwt(USER_EMAIL);

        CompletionStage<Response<String>> result =
            controller.contactUs(auth, null, null);

        result.whenComplete((response, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNull();
            assertThat(response.getError()).isNotNull();
            assertThat(response.getError().getCode()).isEqualTo("INVALID_TYPE");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldSubmitSalesContactRequest(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId(TENANT_ID);
        String token = buildJwt(USER_EMAIL);
        String auth = "Bearer " + token;

        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).name("Acme Corp").build();
        when(tenantService.getTenant(eq(TENANT_ID))).thenReturn(Maybe.just(tenant));
        when(notificationService.sendNotificationAsync(eq("default-project"), any()))
            .thenReturn(Single.just(
                NotificationBatchResponseDto.builder()
                    .idempotencyKey("key-1")
                    .build()));

        ContactRequestDto request = ContactRequestDto.builder()
            .message("I want to know more about pricing")
            .build();

        CompletionStage<Response<String>> result =
            controller.contactUs(auth, "sales", request);

        result.whenComplete((response, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<SendNotificationRequestDto> captor =
                ArgumentCaptor.forClass(SendNotificationRequestDto.class);
            verify(notificationService)
                .sendNotificationAsync(eq("default-project"), captor.capture());

            SendNotificationRequestDto sent = captor.getValue();
            assertThat(sent.getEventName())
                .isEqualTo(NotificationConstants.CONTACT_US_EVENT_NAME);
            assertThat(sent.getParams()).containsEntry("userEmail", USER_EMAIL);
            assertThat(sent.getParams()).containsEntry("tenantName", "Acme Corp");
            assertThat(sent.getParams())
                .containsEntry("message", "I want to know more about pricing");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldSubmitSupportContactRequest(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId(TENANT_ID);
        String token = buildJwt(USER_EMAIL);
        String auth = "Bearer " + token;

        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).name("Acme Corp").build();
        when(tenantService.getTenant(eq(TENANT_ID))).thenReturn(Maybe.just(tenant));
        when(notificationService.sendNotificationAsync(eq("default-project"), any()))
            .thenReturn(Single.just(
                NotificationBatchResponseDto.builder()
                    .idempotencyKey("key-2")
                    .build()));

        ContactRequestDto request = ContactRequestDto.builder()
            .message("I need help with SDK integration")
            .build();

        CompletionStage<Response<String>> result =
            controller.contactUs(auth, "support", request);

        result.whenComplete((response, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<SendNotificationRequestDto> captor =
                ArgumentCaptor.forClass(SendNotificationRequestDto.class);
            verify(notificationService)
                .sendNotificationAsync(eq("default-project"), captor.capture());

            SendNotificationRequestDto sent = captor.getValue();
            assertThat(sent.getEventName())
                .isEqualTo(NotificationConstants.CONTACT_SUPPORT_EVENT_NAME);
            assertThat(sent.getParams()).containsEntry("userEmail", USER_EMAIL);
            assertThat(sent.getParams())
                .containsEntry("message", "I need help with SDK integration");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldHandleCaseInsensitiveEventType(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId(TENANT_ID);
        String auth = "Bearer " + buildJwt(USER_EMAIL);

        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).name("Corp").build();
        when(tenantService.getTenant(eq(TENANT_ID))).thenReturn(Maybe.just(tenant));
        when(notificationService.sendNotificationAsync(any(), any()))
            .thenReturn(Single.just(
                NotificationBatchResponseDto.builder().idempotencyKey("k").build()));

        CompletionStage<Response<String>> result =
            controller.contactUs(auth, "Sales", null);

        result.whenComplete((response, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<SendNotificationRequestDto> captor =
                ArgumentCaptor.forClass(SendNotificationRequestDto.class);
            verify(notificationService).sendNotificationAsync(any(), captor.capture());
            assertThat(captor.getValue().getEventName())
                .isEqualTo(NotificationConstants.CONTACT_US_EVENT_NAME);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldUseTenantIdAsFallbackWhenTenantNotFound(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId(TENANT_ID);
        String auth = "Bearer " + buildJwt(USER_EMAIL);

        when(tenantService.getTenant(eq(TENANT_ID))).thenReturn(Maybe.empty());
        when(notificationService.sendNotificationAsync(any(), any()))
            .thenReturn(Single.just(
                NotificationBatchResponseDto.builder().idempotencyKey("k").build()));

        CompletionStage<Response<String>> result =
            controller.contactUs(auth, "sales", null);

        result.whenComplete((response, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<SendNotificationRequestDto> captor =
                ArgumentCaptor.forClass(SendNotificationRequestDto.class);
            verify(notificationService).sendNotificationAsync(any(), captor.capture());
            assertThat(captor.getValue().getParams())
                .containsEntry("tenantName", TENANT_ID);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldHandleNullRequestBody(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        TenantContext.setTenantId(TENANT_ID);
        String auth = "Bearer " + buildJwt(USER_EMAIL);

        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).name("Corp").build();
        when(tenantService.getTenant(eq(TENANT_ID))).thenReturn(Maybe.just(tenant));
        when(notificationService.sendNotificationAsync(any(), any()))
            .thenReturn(Single.just(
                NotificationBatchResponseDto.builder().idempotencyKey("k").build()));

        CompletionStage<Response<String>> result =
            controller.contactUs(auth, "support", null);

        result.whenComplete((response, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<SendNotificationRequestDto> captor =
                ArgumentCaptor.forClass(SendNotificationRequestDto.class);
            verify(notificationService).sendNotificationAsync(any(), captor.capture());
            assertThat(captor.getValue().getParams().get("message")).isNull();
          });
          testContext.completeNow();
        });
      });
    }
  }
}
