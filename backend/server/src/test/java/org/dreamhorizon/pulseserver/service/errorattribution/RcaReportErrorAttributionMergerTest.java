package org.dreamhorizon.pulseserver.service.errorattribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RcaReportErrorAttributionMergerTest {

  private static final String PROJECT = "p1";
  private static final String INTERACTION = "checkout";

  @Mock private ErrorAttributionService errorAttributionService;

  private ObjectMapper objectMapper;
  private RcaReportErrorAttributionMerger merger;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    merger = new RcaReportErrorAttributionMerger(objectMapper, errorAttributionService);
  }

  @Test
  void shouldSetErrorAttributionUnderStructuredWhenServiceReturnsRows() throws Exception {
    ErrorAttributionRelatedAttributionRow row =
        new ErrorAttributionRelatedAttributionRow(
            "crash",
            ErrorAttributionRelatedAttributions.ROW_KIND_ISSUE,
            "g1",
            "T",
            null,
            null,
            null,
            null,
            null,
            null,
            10L,
            10L,
            100L,
            1L,
            10L,
            0.1,
            0.1,
            2.0,
            false,
            null);
    when(errorAttributionService.getErrorAttributionWithOptionalDrillDown(
            eq(PROJECT), eq(INTERACTION), any(), any(), any()))
        .thenReturn(
            Single.just(new ErrorAttributionWithDrillDown(List.of(row), 2.0d)));

    ObjectNode root = objectMapper.createObjectNode();
    ObjectNode report = root.putObject("report");
    ObjectNode structured = report.putObject("structured");
    structured.put("version", 1);

    LocalDate anchor = LocalDate.of(2026, 4, 10);
    Instant end = Instant.parse("2026-04-10T12:00:00Z");
    merger.mergeInto(root, PROJECT, INTERACTION, anchor, end, 7);

    assertThat(structured.path("errorAttribution").path("relatedAttributions").isArray()).isTrue();
    assertThat(structured.path("errorAttribution").path("relatedAttributions").get(0).path("groupId").asText())
        .isEqualTo("g1");
    verify(errorAttributionService)
        .getErrorAttributionWithOptionalDrillDown(
            eq(PROJECT),
            eq(INTERACTION),
            any(),
            any(),
            eq(
                List.of(
                    ErrorAttributionDrillDownSignal.anr,
                    ErrorAttributionDrillDownSignal.non_fatal,
                    ErrorAttributionDrillDownSignal.api)));
  }

  @Test
  void shouldNoOpWhenStructuredMissing() {
    ObjectNode root = objectMapper.createObjectNode();
    root.putObject("report");
    merger.mergeInto(
        root,
        PROJECT,
        INTERACTION,
        LocalDate.of(2026, 4, 10),
        Instant.parse("2026-04-10T12:00:00Z"),
        7);
    verify(errorAttributionService, never())
        .getErrorAttributionWithOptionalDrillDown(any(), any(), any(), any(), any());
  }
}
