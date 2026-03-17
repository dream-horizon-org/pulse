package org.dreamhorizon.pulseserver.resources.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentRequestDto;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentResponseDto;
import org.dreamhorizon.pulseserver.resources.incident.models.IncidentResponseDto;
import org.dreamhorizon.pulseserver.resources.incident.models.SlackActionPayload;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentSeverity;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentStatus;
import org.dreamhorizon.pulseserver.service.incident.IncidentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentControllerTest {

  @Mock
  private IncidentService incidentService;

  @Test
  void shouldInitializeController() {
    IncidentController controller = new IncidentController(incidentService);
    assertNotNull(controller);
  }

  @Test
  void shouldBuildCreateIncidentRequestDto() {
    CreateIncidentRequestDto dto = CreateIncidentRequestDto.builder()
        .title("Server Down")
        .description("Production server is unresponsive")
        .severity(IncidentSeverity.P1)
        .reporterName("John Doe")
        .reporterEmail("john@example.com")
        .orgIdentifier("org-123")
        .build();

    assertThat(dto.getTitle()).isEqualTo("Server Down");
    assertThat(dto.getDescription()).isEqualTo("Production server is unresponsive");
    assertThat(dto.getSeverity()).isEqualTo(IncidentSeverity.P1);
    assertThat(dto.getReporterName()).isEqualTo("John Doe");
    assertThat(dto.getReporterEmail()).isEqualTo("john@example.com");
    assertThat(dto.getOrgIdentifier()).isEqualTo("org-123");
  }

  @Test
  void shouldBuildCreateIncidentResponseDto() {
    CreateIncidentResponseDto dto = CreateIncidentResponseDto.builder()
        .id(1L)
        .status(IncidentStatus.OPEN)
        .createdAt("2026-03-16T10:00:00")
        .build();

    assertThat(dto.getId()).isEqualTo(1L);
    assertThat(dto.getStatus()).isEqualTo(IncidentStatus.OPEN);
    assertThat(dto.getCreatedAt()).isEqualTo("2026-03-16T10:00:00");
  }

  @Test
  void shouldBuildIncidentResponseDto() {
    IncidentResponseDto dto = IncidentResponseDto.builder()
        .id(1L)
        .title("Server Down")
        .description("Unresponsive")
        .severity(IncidentSeverity.P1)
        .status(IncidentStatus.ACKNOWLEDGED)
        .reporterName("John Doe")
        .reporterEmail("john@example.com")
        .createdAt("2026-03-16T10:00:00")
        .updatedAt("2026-03-16T11:00:00")
        .build();

    assertThat(dto.getId()).isEqualTo(1L);
    assertThat(dto.getTitle()).isEqualTo("Server Down");
    assertThat(dto.getDescription()).isEqualTo("Unresponsive");
    assertThat(dto.getSeverity()).isEqualTo(IncidentSeverity.P1);
    assertThat(dto.getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
    assertThat(dto.getReporterName()).isEqualTo("John Doe");
    assertThat(dto.getReporterEmail()).isEqualTo("john@example.com");
    assertThat(dto.getCreatedAt()).isEqualTo("2026-03-16T10:00:00");
    assertThat(dto.getUpdatedAt()).isEqualTo("2026-03-16T11:00:00");
  }

  @Test
  void shouldBuildSlackActionPayloadWithNestedClasses() {
    SlackActionPayload.User user = SlackActionPayload.User.builder()
        .id("U12345")
        .username("testuser")
        .name("Test User")
        .build();

    SlackActionPayload.Action action = SlackActionPayload.Action.builder()
        .type("button")
        .actionId("ack")
        .value("42")
        .build();

    SlackActionPayload payload = SlackActionPayload.builder()
        .type("block_actions")
        .user(user)
        .triggerId("trigger-123")
        .actions(List.of(action))
        .build();

    assertThat(payload.getType()).isEqualTo("block_actions");
    assertThat(payload.getUser().getId()).isEqualTo("U12345");
    assertThat(payload.getUser().getUsername()).isEqualTo("testuser");
    assertThat(payload.getUser().getName()).isEqualTo("Test User");
    assertThat(payload.getTriggerId()).isEqualTo("trigger-123");
    assertThat(payload.getActions()).hasSize(1);
    assertThat(payload.getActions().get(0).getType()).isEqualTo("button");
    assertThat(payload.getActions().get(0).getActionId()).isEqualTo("ack");
    assertThat(payload.getActions().get(0).getValue()).isEqualTo("42");
  }
}
