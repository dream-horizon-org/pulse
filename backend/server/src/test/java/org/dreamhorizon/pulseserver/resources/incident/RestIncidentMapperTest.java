package org.dreamhorizon.pulseserver.resources.incident;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.dao.incidentdao.models.IncidentRow;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentRequestDto;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentResponseDto;
import org.dreamhorizon.pulseserver.resources.incident.models.IncidentResponseDto;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentSeverity;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RestIncidentMapperTest {

  private final RestIncidentMapper mapper = RestIncidentMapper.INSTANCE;

  private IncidentRow buildFullRow() {
    return IncidentRow.builder()
        .id(1L)
        .title("Server Down")
        .description("Production server is unresponsive")
        .severity(IncidentSeverity.P1)
        .reporterName("John Doe")
        .reporterEmail("john@example.com")
        .orgIdentifier("org-123")
        .status(IncidentStatus.OPEN)
        .createdAt("2026-03-16T10:00:00")
        .updatedAt("2026-03-16T10:30:00")
        .acknowledgedAt("2026-03-16T11:00:00")
        .recoveredAt("2026-03-16T12:00:00")
        .closedAt("2026-03-16T13:00:00")
        .build();
  }

  @Nested
  class ToIncidentResponseDto {

    @Test
    void shouldMapAllFieldsCorrectly() {
      IncidentRow row = buildFullRow();

      IncidentResponseDto dto = mapper.toIncidentResponseDto(row);

      assertThat(dto.getId()).isEqualTo(1L);
      assertThat(dto.getTitle()).isEqualTo("Server Down");
      assertThat(dto.getDescription()).isEqualTo("Production server is unresponsive");
      assertThat(dto.getSeverity()).isEqualTo(IncidentSeverity.P1);
      assertThat(dto.getStatus()).isEqualTo(IncidentStatus.OPEN);
      assertThat(dto.getReporterName()).isEqualTo("John Doe");
      assertThat(dto.getReporterEmail()).isEqualTo("john@example.com");
      assertThat(dto.getCreatedAt()).isEqualTo("2026-03-16T10:00:00");
      assertThat(dto.getUpdatedAt()).isEqualTo("2026-03-16T10:30:00");
    }

    @Test
    void shouldHandleNullOptionalFields() {
      IncidentRow row = IncidentRow.builder()
          .id(2L)
          .title("Minimal")
          .status(IncidentStatus.OPEN)
          .build();

      IncidentResponseDto dto = mapper.toIncidentResponseDto(row);

      assertThat(dto.getId()).isEqualTo(2L);
      assertThat(dto.getTitle()).isEqualTo("Minimal");
      assertThat(dto.getStatus()).isEqualTo(IncidentStatus.OPEN);
      assertThat(dto.getDescription()).isNull();
      assertThat(dto.getSeverity()).isNull();
      assertThat(dto.getReporterName()).isNull();
      assertThat(dto.getReporterEmail()).isNull();
      assertThat(dto.getCreatedAt()).isNull();
      assertThat(dto.getUpdatedAt()).isNull();
    }
  }

  @Nested
  class ToCreateIncidentResponseDto {

    @Test
    void shouldMapIdStatusCreatedAt() {
      IncidentRow row = buildFullRow();

      CreateIncidentResponseDto dto = mapper.toCreateIncidentResponseDto(row);

      assertThat(dto.getId()).isEqualTo(1L);
      assertThat(dto.getStatus()).isEqualTo(IncidentStatus.OPEN);
      assertThat(dto.getCreatedAt()).isEqualTo("2026-03-16T10:00:00");
    }

    @Test
    void shouldHandleNullCreatedAt() {
      IncidentRow row = IncidentRow.builder()
          .id(3L)
          .status(IncidentStatus.ACKNOWLEDGED)
          .createdAt(null)
          .build();

      CreateIncidentResponseDto dto = mapper.toCreateIncidentResponseDto(row);

      assertThat(dto.getId()).isEqualTo(3L);
      assertThat(dto.getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
      assertThat(dto.getCreatedAt()).isNull();
    }
  }

  @Nested
  class ToIncidentRow {

    @Test
    void shouldMapRequestFieldsAndSetProjectId() {
      CreateIncidentRequestDto request = CreateIncidentRequestDto.builder()
          .title("API Outage")
          .description("REST API returning 500s")
          .severity(IncidentSeverity.P2)
          .reporterName("Jane Doe")
          .reporterEmail("jane@example.com")
          .orgIdentifier("org-456")
          .build();

      IncidentRow row = mapper.toIncidentRow(request, "project-xyz");

      assertThat(row.getTitle()).isEqualTo("API Outage");
      assertThat(row.getDescription()).isEqualTo("REST API returning 500s");
      assertThat(row.getSeverity()).isEqualTo(IncidentSeverity.P2);
      assertThat(row.getReporterName()).isEqualTo("Jane Doe");
      assertThat(row.getReporterEmail()).isEqualTo("jane@example.com");
      // orgIdentifier is mapped from projectId via @Mapping
      assertThat(row.getOrgIdentifier()).isEqualTo("project-xyz");
    }

    @Test
    void shouldSetStatusToOpen() {
      CreateIncidentRequestDto request = CreateIncidentRequestDto.builder()
          .title("Test")
          .build();

      IncidentRow row = mapper.toIncidentRow(request, "proj-1");

      assertThat(row.getStatus()).isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    void shouldIgnoreIdAndTimestampFields() {
      CreateIncidentRequestDto request = CreateIncidentRequestDto.builder()
          .title("Test Incident")
          .description("Testing ignored fields")
          .severity(IncidentSeverity.P3)
          .reporterName("Reporter")
          .reporterEmail("reporter@test.com")
          .orgIdentifier("org-1")
          .build();

      IncidentRow row = mapper.toIncidentRow(request, "proj-1");

      assertThat(row.getId()).isNull();
      assertThat(row.getCreatedAt()).isNull();
      assertThat(row.getUpdatedAt()).isNull();
      assertThat(row.getAcknowledgedAt()).isNull();
      assertThat(row.getRecoveredAt()).isNull();
      assertThat(row.getClosedAt()).isNull();
    }

    @Test
    void shouldMapOrgIdentifierFromProjectId() {
      CreateIncidentRequestDto request = CreateIncidentRequestDto.builder()
          .title("Test")
          .orgIdentifier("original-org")
          .build();

      IncidentRow row = mapper.toIncidentRow(request, "overridden-project");

      // The @Mapping(target = "orgIdentifier", source = "projectId") overrides request.orgIdentifier
      assertThat(row.getOrgIdentifier()).isEqualTo("overridden-project");
    }
  }
}
