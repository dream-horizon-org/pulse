package org.dreamhorizon.pulseserver.service.rootcause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto.RowField;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto.Row;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.service.rootcause.models.SessionEvidenceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionEvidenceServiceImplTest {

  private static final String PROJECT_ID = "test-project";
  private static final String INTERACTION_NAME = "checkout";
  private static final Instant START_TIME = Instant.parse("2025-03-01T00:00:00Z");
  private static final Instant END_TIME = Instant.parse("2025-03-08T00:00:00Z");

  @Mock private ClickhouseQueryService clickhouseQueryService;

  private SessionEvidenceServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new SessionEvidenceServiceImpl(clickhouseQueryService);
  }

  @Nested
  class GetSessionEvidenceWithMetrics {

    @Test
    void shouldReturnSessionEvidenceWhenClickhouseReturnsData() {
      // Setup
      Map<String, String> dimensions = Map.of("Platform", "Android");
      Map<String, Double> metrics = Map.of("error_rate", 5.0, "apdex", 0.5);

      Row row1 = createRow("session-1");
      Row row2 = createRow("session-2");
      GetRawUserEventsResponseDto data =
          GetRawUserEventsResponseDto.builder()
              .rows(List.of(row1, row2))
              .build();
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> queryResponse =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
              .data(data)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(queryResponse));

      // Execute
      SessionEvidenceResult result =
          service
              .getSessionEvidence(
                  PROJECT_ID,
                  INTERACTION_NAME,
                  START_TIME,
                  END_TIME,
                  dimensions,
                  metrics,
                  5)
              .blockingGet();

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getSessions()).hasSize(2);
      assertThat(result.getSessions().get(0).getSessionId()).isEqualTo("session-1");
      assertThat(result.getSessions().get(1).getSessionId()).isEqualTo("session-2");
      assertThat(result.getTotalSessionsCount()).isEqualTo(2);
      verify(clickhouseQueryService, times(1)).executeQueryOrCreateJob(any());
    }

    @Test
    void shouldReturnEmptyListWhenClickhouseReturnsNoRows() {
      // Setup
      Map<String, String> dimensions = Map.of("Platform", "iOS");
      Map<String, Double> metrics = Map.of("error_rate", 10.0, "apdex", 0.3);

      GetRawUserEventsResponseDto data =
          GetRawUserEventsResponseDto.builder()
              .rows(new ArrayList<>())
              .build();
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> queryResponse =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
              .data(data)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(queryResponse));

      // Execute
      SessionEvidenceResult result =
          service
              .getSessionEvidence(
                  PROJECT_ID,
                  INTERACTION_NAME,
                  START_TIME,
                  END_TIME,
                  dimensions,
                  metrics,
                  5)
              .blockingGet();

      // Assert
      assertThat(result.getSessions()).isEmpty();
      assertThat(result.getTotalSessionsCount()).isEqualTo(0);
    }

    @Test
    void shouldReturnEmptyListWhenResponseDataIsNull() {
      // Setup
      Map<String, String> dimensions = Map.of("Platform", "Android");
      Map<String, Double> metrics = Map.of("error_rate", 5.0, "apdex", 0.5);

      GetQueryDataResponseDto<GetRawUserEventsResponseDto> queryResponse =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
              .data(null)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(queryResponse));

      // Execute
      SessionEvidenceResult result =
          service
              .getSessionEvidence(
                  PROJECT_ID,
                  INTERACTION_NAME,
                  START_TIME,
                  END_TIME,
                  dimensions,
                  metrics,
                  5)
              .blockingGet();

      // Assert
      assertThat(result.getSessions()).isEmpty();
      assertThat(result.getTotalSessionsCount()).isEqualTo(0);
    }

    @Test
    void shouldHandleExceptionAndReturnEmptyList() {
      // Setup
      Map<String, String> dimensions = Map.of("Platform", "Android");
      Map<String, Double> metrics = Map.of("error_rate", 5.0, "apdex", 0.5);

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.error(new RuntimeException("ClickHouse connection failed")));

      // Execute
      SessionEvidenceResult result =
          service
              .getSessionEvidence(
                  PROJECT_ID,
                  INTERACTION_NAME,
                  START_TIME,
                  END_TIME,
                  dimensions,
                  metrics,
                  5)
              .blockingGet();

      // Assert
      assertThat(result.getSessions()).isEmpty();
      assertThat(result.getTotalSessionsCount()).isEqualTo(0);
    }

    @Test
    void shouldSkipRowsWithNullOrMissingFields() {
      // Setup
      Map<String, String> dimensions = Map.of("Platform", "Android");
      Map<String, Double> metrics = Map.of("error_rate", 5.0, "apdex", 0.5);

      Row validRow = createRow("session-1");
      Row invalidRow = new Row();
      invalidRow.setRowFields(null);

      GetRawUserEventsResponseDto data =
          GetRawUserEventsResponseDto.builder()
              .rows(List.of(validRow, invalidRow))
              .build();
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> queryResponse =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
              .data(data)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(queryResponse));

      // Execute
      SessionEvidenceResult result =
          service
              .getSessionEvidence(
                  PROJECT_ID,
                  INTERACTION_NAME,
                  START_TIME,
                  END_TIME,
                  dimensions,
                  metrics,
                  5)
              .blockingGet();

      // Assert - only valid row should be parsed
      assertThat(result.getSessions()).hasSize(1);
      assertThat(result.getSessions().get(0).getSessionId()).isEqualTo("session-1");
    }
  }

  @Nested
  class GetSessionEvidenceWithoutMetrics {

    @Test
    void shouldUseBackwardCompatibleOverloadWithoutMetrics() {
      // Setup
      Map<String, String> dimensions = Map.of("Platform", "iOS");

      Row row1 = createRow("session-1");
      GetRawUserEventsResponseDto data =
          GetRawUserEventsResponseDto.builder()
              .rows(List.of(row1))
              .build();
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> queryResponse =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
              .data(data)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(queryResponse));

      // Execute
      SessionEvidenceResult result =
          service
              .getSessionEvidence(
                  PROJECT_ID,
                  INTERACTION_NAME,
                  START_TIME,
                  END_TIME,
                  dimensions,
                  5)
              .blockingGet();

      // Assert
      assertThat(result.getSessions()).hasSize(1);
      assertThat(result.getSessions().get(0).getSessionId()).isEqualTo("session-1");
    }
  }

  @Nested
  class EdgeCases {

    @Test
    void shouldHandleEmptyDimensions() {
      // Setup
      Map<String, String> emptyDimensions = Map.of();
      Map<String, Double> metrics = Map.of("error_rate", 5.0, "apdex", 0.5);

      GetRawUserEventsResponseDto data =
          GetRawUserEventsResponseDto.builder()
              .rows(new ArrayList<>())
              .build();
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> queryResponse =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
              .data(data)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(queryResponse));

      // Execute
      SessionEvidenceResult result =
          service
              .getSessionEvidence(
                  PROJECT_ID,
                  INTERACTION_NAME,
                  START_TIME,
                  END_TIME,
                  emptyDimensions,
                  metrics,
                  5)
              .blockingGet();

      // Assert
      assertThat(result.getSessions()).isEmpty();
    }

    @Test
    void shouldHandleLargeNumberOfSessions() {
      // Setup
      Map<String, String> dimensions = Map.of("Platform", "Android");
      Map<String, Double> metrics = Map.of("error_rate", 5.0, "apdex", 0.5);

      List<Row> rows = new ArrayList<>();
      for (int i = 0; i < 50; i++) {
        rows.add(createRow("session-" + i));
      }

      GetRawUserEventsResponseDto data =
          GetRawUserEventsResponseDto.builder()
              .rows(rows)
              .build();
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> queryResponse =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
              .data(data)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(queryResponse));

      // Execute
      SessionEvidenceResult result =
          service
              .getSessionEvidence(
                  PROJECT_ID,
                  INTERACTION_NAME,
                  START_TIME,
                  END_TIME,
                  dimensions,
                  metrics,
                  50)
              .blockingGet();

      // Assert
      assertThat(result.getSessions()).hasSize(50);
      assertThat(result.getTotalSessionsCount()).isEqualTo(50);
    }

    @Test
    void shouldHandleRowsWithEmptyFieldsList() {
      // Setup
      Map<String, String> dimensions = Map.of("Platform", "Android");
      Map<String, Double> metrics = Map.of("error_rate", 5.0, "apdex", 0.5);

      Row emptyFieldsRow = new Row();
      emptyFieldsRow.setRowFields(new ArrayList<>());

      GetRawUserEventsResponseDto data =
          GetRawUserEventsResponseDto.builder()
              .rows(List.of(emptyFieldsRow))
              .build();
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> queryResponse =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
              .data(data)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(queryResponse));

      // Execute
      SessionEvidenceResult result =
          service
              .getSessionEvidence(
                  PROJECT_ID,
                  INTERACTION_NAME,
                  START_TIME,
                  END_TIME,
                  dimensions,
                  metrics,
                  5)
              .blockingGet();

      // Assert
      assertThat(result.getSessions()).isEmpty();
    }
  }

  private Row createRow(String sessionId) {
    RowField sessionField = new RowField();
    sessionField.setValue(sessionId);
    Row row = new Row();
    row.setRowFields(List.of(sessionField));
    return row;
  }
}
