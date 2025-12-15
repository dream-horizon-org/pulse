package org.dreamhorizon.pulseserver.resources.alert.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dreamhorizon.pulseserver.resources.alert.models.AlertDetailsPaginatedResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertDetailsResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertFiltersResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertMetricsResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertNotificationChannelResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertScopesResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertSeverityResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertTagMapRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertTagsResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AllAlertDetailsResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.CreateAlertNotificationChannelRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.CreateAlertSeverityRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.CreateTagRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.EvaluateAlertRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.EvaluateAlertResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.GetAlertsListRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.ScopeEvaluationHistoryDto;
import org.dreamhorizon.pulseserver.resources.alert.models.SnoozeAlertRestRequest;
import org.dreamhorizon.pulseserver.resources.alert.models.SnoozeAlertRestResponse;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthHeaders;
import org.dreamhorizon.pulseserver.service.alert.core.AlertEvaluationService;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;
import org.dreamhorizon.pulseserver.service.alert.core.models.DeleteSnoozeRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.GenericSuccessResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertControllersV1Test {

  @Mock
  private AlertService alertService;

  @Mock
  private AlertEvaluationService alertEvaluationService;

  // Controller initialization tests

  @Nested
  class TestGetAlertsController {

    @Test
    void shouldInitializeController() {
      GetAlerts controller = new GetAlerts(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestGetAlertDetailsController {

    @Test
    void shouldInitializeController() {
      GetAlertDetails controller = new GetAlertDetails(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestGetAllAlertsController {

    @Test
    void shouldInitializeController() {
      GetAllAlerts controller = new GetAllAlerts(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestDeleteAlertController {

    @Test
    void shouldInitializeController() {
      DeleteAlert controller = new DeleteAlert(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestSnoozeAlertController {

    @Test
    void shouldInitializeController() {
      SnoozeAlert controller = new SnoozeAlert(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestDeleteSnoozeController {

    @Test
    void shouldInitializeController() {
      DeleteSnooze controller = new DeleteSnooze(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestEvaluateAndTriggerAlertController {

    @Test
    void shouldInitializeController() {
      EvaluateAndTriggerAlert controller = new EvaluateAndTriggerAlert(alertEvaluationService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestGetAlertFiltersController {

    @Test
    void shouldInitializeController() {
      GetAlertFilters controller = new GetAlertFilters(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestGetAlertMetricsController {

    @Test
    void shouldInitializeController() {
      GetAlertMetrics controller = new GetAlertMetrics(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestGetAlertScopesController {

    @Test
    void shouldInitializeController() {
      GetAlertScopes controller = new GetAlertScopes(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestGetAlertSeverityListController {

    @Test
    void shouldInitializeController() {
      GetAlertSeverityList controller = new GetAlertSeverityList(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestCreateAlertSeverityController {

    @Test
    void shouldInitializeController() {
      CreateAlertSeverity controller = new CreateAlertSeverity(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestGetAlertNotificationChannelsController {

    @Test
    void shouldInitializeController() {
      GetAlertNotificationChannels controller = new GetAlertNotificationChannels(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestCreateAlertNotificationChannelController {

    @Test
    void shouldInitializeController() {
      CreateAlertNotificationChannel controller = new CreateAlertNotificationChannel(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestCreateTagController {

    @Test
    void shouldInitializeController() {
      CreateTag controller = new CreateTag(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestGetAllTagsController {

    @Test
    void shouldInitializeController() {
      GetAllTags controller = new GetAllTags(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestAddTagToAlertController {

    @Test
    void shouldInitializeController() {
      AddTagToAlert controller = new AddTagToAlert(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestGetTagsForAlertController {

    @Test
    void shouldInitializeController() {
      GetTagsForAlert controller = new GetTagsForAlert(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestDeleteTagFromAlertController {

    @Test
    void shouldInitializeController() {
      DeleteTagFromAlert controller = new DeleteTagFromAlert(alertService);
      assertNotNull(controller);
    }
  }

  @Nested
  class TestGetAlertEvaluationHistoryController {

    @Test
    void shouldInitializeController() {
      GetAlertEvaluationHistory controller = new GetAlertEvaluationHistory(alertService);
      assertNotNull(controller);
    }
  }

  // DTO Model Tests

  @Nested
  class TestAuthHeadersModel {

    @Test
    void shouldCreateWithAllArgs() {
      AuthHeaders headers = new AuthHeaders("auth-token", "user@test.com");

      assertEquals("auth-token", headers.getAuthorization());
      assertEquals("user@test.com", headers.getUserEmail());
    }

    @Test
    void shouldCreateWithNoArgs() {
      AuthHeaders headers = new AuthHeaders();

      assertNotNull(headers);
    }

    @Test
    void shouldSetAndGetFields() {
      AuthHeaders headers = new AuthHeaders();
      headers.setAuthorization("new-auth-token");
      headers.setUserEmail("new-user@test.com");

      assertEquals("new-auth-token", headers.getAuthorization());
      assertEquals("new-user@test.com", headers.getUserEmail());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AuthHeaders headers1 = new AuthHeaders("auth", "user@test.com");
      AuthHeaders headers2 = new AuthHeaders("auth", "user@test.com");

      assertEquals(headers1, headers2);
      assertEquals(headers1.hashCode(), headers2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      AuthHeaders headers = new AuthHeaders("auth", "user@test.com");

      String toString = headers.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("user@test.com"));
    }
  }

  @Nested
  class TestCreateTagRequestDtoModel {

    @Test
    void shouldCreateWithAllArgs() {
      CreateTagRequestDto dto = new CreateTagRequestDto("test-tag");

      assertEquals("test-tag", dto.getTag());
    }

    @Test
    void shouldCreateWithNoArgs() {
      CreateTagRequestDto dto = new CreateTagRequestDto();

      assertNotNull(dto);
    }

    @Test
    void shouldSetAndGetTag() {
      CreateTagRequestDto dto = new CreateTagRequestDto();
      dto.setTag("new-tag");

      assertEquals("new-tag", dto.getTag());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      CreateTagRequestDto dto1 = new CreateTagRequestDto("tag");
      CreateTagRequestDto dto2 = new CreateTagRequestDto("tag");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      CreateTagRequestDto dto = new CreateTagRequestDto("test-tag");

      String toString = dto.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("test-tag"));
    }
  }

  @Nested
  class TestEvaluateAlertRequestDtoModel {

    @Test
    void shouldCreateWithAllArgs() {
      EvaluateAlertRequestDto dto = new EvaluateAlertRequestDto(1);

      assertEquals(1, dto.getAlertId());
    }

    @Test
    void shouldCreateWithNoArgs() {
      EvaluateAlertRequestDto dto = new EvaluateAlertRequestDto();

      assertNotNull(dto);
    }

    @Test
    void shouldSetAndGetAlertId() {
      EvaluateAlertRequestDto dto = new EvaluateAlertRequestDto();
      dto.setAlertId(2);

      assertEquals(2, dto.getAlertId());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      EvaluateAlertRequestDto dto1 = new EvaluateAlertRequestDto(1);
      EvaluateAlertRequestDto dto2 = new EvaluateAlertRequestDto(1);

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      EvaluateAlertRequestDto dto = new EvaluateAlertRequestDto(1);

      String toString = dto.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("1"));
    }
  }

  @Nested
  class TestGenericSuccessResponseModel {

    @Test
    void shouldCreateWithBuilder() {
      GenericSuccessResponse response = GenericSuccessResponse.builder()
          .status("success")
          .build();

      assertEquals("success", response.getStatus());
    }

    @Test
    void shouldCreateWithAllArgs() {
      GenericSuccessResponse response = new GenericSuccessResponse("success");

      assertEquals("success", response.getStatus());
    }

    @Test
    void shouldCreateWithNoArgs() {
      GenericSuccessResponse response = new GenericSuccessResponse();

      assertNotNull(response);
    }

    @Test
    void shouldSetAndGetStatus() {
      GenericSuccessResponse response = new GenericSuccessResponse();
      response.setStatus("failed");

      assertEquals("failed", response.getStatus());
    }
  }

  @Nested
  class TestAlertTagMapRequestDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      AlertTagMapRequestDto dto = new AlertTagMapRequestDto();

      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      AlertTagMapRequestDto dto = new AlertTagMapRequestDto(1);

      assertEquals(1, dto.getTagId());
    }

    @Test
    void shouldSetAndGetTagId() {
      AlertTagMapRequestDto dto = new AlertTagMapRequestDto();
      dto.setTagId(2);

      assertEquals(2, dto.getTagId());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertTagMapRequestDto dto1 = new AlertTagMapRequestDto(1);
      AlertTagMapRequestDto dto2 = new AlertTagMapRequestDto(1);

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  @Nested
  class TestDeleteSnoozeRequestModel {

    @Test
    void shouldBuildDeleteSnoozeRequest() {
      DeleteSnoozeRequest request = DeleteSnoozeRequest.builder()
          .alertId(1)
          .updatedBy("user@test.com")
          .build();

      assertEquals(1, request.getAlertId());
      assertEquals("user@test.com", request.getUpdatedBy());
    }

    @Test
    void shouldBuildWithDifferentValues() {
      DeleteSnoozeRequest request = DeleteSnoozeRequest.builder()
          .alertId(42)
          .updatedBy("another@test.com")
          .build();

      assertEquals(42, request.getAlertId());
      assertEquals("another@test.com", request.getUpdatedBy());
    }

    @Test
    void shouldBuildWithNullValues() {
      DeleteSnoozeRequest request = DeleteSnoozeRequest.builder()
          .alertId(null)
          .updatedBy(null)
          .build();

      assertNotNull(request);
    }
  }

  @Nested
  class TestGetAlertsListRequestDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      GetAlertsListRequestDto dto = new GetAlertsListRequestDto();

      assertNotNull(dto);
    }

    @Test
    void shouldSetAndGetFields() {
      GetAlertsListRequestDto dto = new GetAlertsListRequestDto();
      dto.setLimit(10);
      dto.setOffset(0);

      assertEquals(10, dto.getLimit());
      assertEquals(0, dto.getOffset());
    }
  }

  @Nested
  class TestCreateAlertSeverityRequestDtoModel {

    @Test
    void shouldCreateWithAllArgs() {
      CreateAlertSeverityRequestDto dto = new CreateAlertSeverityRequestDto(1, "high");

      assertEquals(1, dto.getName());
      assertEquals("high", dto.getDescription());
    }

    @Test
    void shouldCreateWithNoArgs() {
      CreateAlertSeverityRequestDto dto = new CreateAlertSeverityRequestDto();

      assertNotNull(dto);
    }

    @Test
    void shouldSetAndGetFields() {
      CreateAlertSeverityRequestDto dto = new CreateAlertSeverityRequestDto();
      dto.setName(2);
      dto.setDescription("medium");

      assertEquals(2, dto.getName());
      assertEquals("medium", dto.getDescription());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      CreateAlertSeverityRequestDto dto1 = new CreateAlertSeverityRequestDto(1, "high");
      CreateAlertSeverityRequestDto dto2 = new CreateAlertSeverityRequestDto(1, "high");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  @Nested
  class TestCreateAlertNotificationChannelRequestDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      CreateAlertNotificationChannelRequestDto dto = new CreateAlertNotificationChannelRequestDto();

      assertNotNull(dto);
    }

    @Test
    void shouldCreateWithAllArgs() {
      CreateAlertNotificationChannelRequestDto dto = new CreateAlertNotificationChannelRequestDto("email", "{\"url\":\"test\"}");

      assertEquals("email", dto.getName());
      assertEquals("{\"url\":\"test\"}", dto.getConfig());
    }

    @Test
    void shouldSetAndGetFields() {
      CreateAlertNotificationChannelRequestDto dto = new CreateAlertNotificationChannelRequestDto();
      dto.setName("slack");
      dto.setConfig("{\"webhook\":\"url\"}");

      assertEquals("slack", dto.getName());
      assertEquals("{\"webhook\":\"url\"}", dto.getConfig());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      CreateAlertNotificationChannelRequestDto dto1 = new CreateAlertNotificationChannelRequestDto("slack", "{}");
      CreateAlertNotificationChannelRequestDto dto2 = new CreateAlertNotificationChannelRequestDto("slack", "{}");

      assertEquals(dto1, dto2);
      assertEquals(dto1.hashCode(), dto2.hashCode());
    }
  }

  @Nested
  class TestAlertFiltersResponseDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      AlertFiltersResponseDto dto = new AlertFiltersResponseDto();

      assertNotNull(dto);
    }
  }

  @Nested
  class TestAlertMetricsResponseDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      AlertMetricsResponseDto dto = new AlertMetricsResponseDto();

      assertNotNull(dto);
    }
  }

  @Nested
  class TestAlertScopesResponseDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      AlertScopesResponseDto dto = new AlertScopesResponseDto();

      assertNotNull(dto);
    }
  }

  @Nested
  class TestAlertSeverityResponseDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      AlertSeverityResponseDto dto = new AlertSeverityResponseDto();

      assertNotNull(dto);
    }
  }

  @Nested
  class TestAlertNotificationChannelResponseDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      AlertNotificationChannelResponseDto dto = new AlertNotificationChannelResponseDto();

      assertNotNull(dto);
    }
  }

  @Nested
  class TestAlertTagsResponseDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      AlertTagsResponseDto dto = new AlertTagsResponseDto();

      assertNotNull(dto);
    }
  }

  @Nested
  class TestScopeEvaluationHistoryDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      ScopeEvaluationHistoryDto dto = new ScopeEvaluationHistoryDto();

      assertNotNull(dto);
    }
  }

  @Nested
  class TestAlertDetailsPaginatedResponseDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      AlertDetailsPaginatedResponseDto dto = new AlertDetailsPaginatedResponseDto();

      assertNotNull(dto);
    }
  }

  @Nested
  class TestAlertDetailsResponseDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      AlertDetailsResponseDto dto = new AlertDetailsResponseDto();

      assertNotNull(dto);
    }
  }

  @Nested
  class TestAllAlertDetailsResponseDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      AllAlertDetailsResponseDto dto = new AllAlertDetailsResponseDto();

      assertNotNull(dto);
    }
  }

  @Nested
  class TestEvaluateAlertResponseDtoModel {

    @Test
    void shouldCreateWithNoArgs() {
      EvaluateAlertResponseDto dto = new EvaluateAlertResponseDto();

      assertNotNull(dto);
    }
  }

  @Nested
  class TestSnoozeAlertRestRequestModel {

    @Test
    void shouldCreateWithNoArgs() {
      SnoozeAlertRestRequest request = new SnoozeAlertRestRequest();

      assertNotNull(request);
    }

    @Test
    void shouldCreateWithAllArgs() {
      Long snoozeFrom = System.currentTimeMillis();
      Long snoozeUntil = snoozeFrom + 3600000;
      SnoozeAlertRestRequest request = new SnoozeAlertRestRequest(snoozeFrom, snoozeUntil);

      assertEquals(snoozeFrom, request.getSnoozeFrom());
      assertEquals(snoozeUntil, request.getSnoozeUntil());
    }

    @Test
    void shouldSetAndGetFields() {
      SnoozeAlertRestRequest request = new SnoozeAlertRestRequest();
      Long snoozeFrom = System.currentTimeMillis();
      Long snoozeUntil = snoozeFrom + 3600000;

      request.setSnoozeFrom(snoozeFrom);
      request.setSnoozeUntil(snoozeUntil);

      assertEquals(snoozeFrom, request.getSnoozeFrom());
      assertEquals(snoozeUntil, request.getSnoozeUntil());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      Long snoozeFrom = System.currentTimeMillis();
      Long snoozeUntil = snoozeFrom + 3600000;
      SnoozeAlertRestRequest request1 = new SnoozeAlertRestRequest(snoozeFrom, snoozeUntil);
      SnoozeAlertRestRequest request2 = new SnoozeAlertRestRequest(snoozeFrom, snoozeUntil);

      assertEquals(request1, request2);
      assertEquals(request1.hashCode(), request2.hashCode());
    }
  }

  @Nested
  class TestSnoozeAlertRestResponseModel {

    @Test
    void shouldCreateWithNoArgs() {
      SnoozeAlertRestResponse response = new SnoozeAlertRestResponse();

      assertNotNull(response);
    }

    @Test
    void shouldCreateWithAllArgs() {
      Long snoozeFrom = System.currentTimeMillis();
      Long snoozeUntil = snoozeFrom + 3600000;
      SnoozeAlertRestResponse response = new SnoozeAlertRestResponse(true, snoozeFrom, snoozeUntil);

      assertTrue(response.getIsSnoozed());
      assertEquals(snoozeFrom, response.getSnoozedFrom());
      assertEquals(snoozeUntil, response.getSnoozedUntil());
    }

    @Test
    void shouldSetAndGetFields() {
      SnoozeAlertRestResponse response = new SnoozeAlertRestResponse();
      Long snoozeFrom = System.currentTimeMillis();
      Long snoozeUntil = snoozeFrom + 3600000;

      response.setIsSnoozed(false);
      response.setSnoozedFrom(snoozeFrom);
      response.setSnoozedUntil(snoozeUntil);

      assertFalse(response.getIsSnoozed());
      assertEquals(snoozeFrom, response.getSnoozedFrom());
      assertEquals(snoozeUntil, response.getSnoozedUntil());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      Long snoozeFrom = System.currentTimeMillis();
      Long snoozeUntil = snoozeFrom + 3600000;
      SnoozeAlertRestResponse response1 = new SnoozeAlertRestResponse(true, snoozeFrom, snoozeUntil);
      SnoozeAlertRestResponse response2 = new SnoozeAlertRestResponse(true, snoozeFrom, snoozeUntil);

      assertEquals(response1, response2);
      assertEquals(response1.hashCode(), response2.hashCode());
    }
  }
}
