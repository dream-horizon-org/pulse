package org.dreamhorizon.pulseserver.service.interaction.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SuggestedInteractionDetailsTest {

  private static final Timestamp TEST_TIMESTAMP = Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 10, 30));

  @Nested
  class BuilderAndGetters {

    @Test
    void shouldBuildWithAllFieldsAndReturnCorrectValues() {
      SuggestedInteractionEdge edge = SuggestedInteractionEdge.builder()
          .from("A").to("B").meanGapS(0.5).medianGapS(0.4).cv(0.1).p5S(0.05).p95S(1.2).build();

      SuggestedInteractionDetails details = SuggestedInteractionDetails.builder()
          .id(42L)
          .projectId("test-project")
          .pattern(List.of("StepOne", "StepTwo"))
          .totalOccurrences(100)
          .uniqueSessions(80)
          .sessionPct(12.5)
          .meanSpanS(1.1)
          .medianSpanS(0.9)
          .p95SpanS(3.2)
          .cv(0.15)
          .edges(List.of(edge))
          .status("PENDING")
          .createdAt(TEST_TIMESTAMP)
          .build();

      assertThat(details.getId()).isEqualTo(42L);
      assertThat(details.getProjectId()).isEqualTo("test-project");
      assertThat(details.getPattern()).containsExactly("StepOne", "StepTwo");
      assertThat(details.getTotalOccurrences()).isEqualTo(100);
      assertThat(details.getUniqueSessions()).isEqualTo(80);
      assertThat(details.getSessionPct()).isEqualTo(12.5);
      assertThat(details.getMeanSpanS()).isEqualTo(1.1);
      assertThat(details.getMedianSpanS()).isEqualTo(0.9);
      assertThat(details.getP95SpanS()).isEqualTo(3.2);
      assertThat(details.getCv()).isEqualTo(0.15);
      assertThat(details.getEdges()).hasSize(1);
      assertThat(details.getEdges().get(0).getFrom()).isEqualTo("A");
      assertThat(details.getStatus()).isEqualTo("PENDING");
      assertThat(details.getCreatedAt()).isEqualTo(TEST_TIMESTAMP);
    }
  }

  @Nested
  class SettersAndNoArgConstructor {

    @Test
    void shouldSetAndGetAllFields() {
      SuggestedInteractionDetails details = new SuggestedInteractionDetails();
      details.setId(1L);
      details.setProjectId("proj");
      details.setPattern(List.of("EventA"));
      details.setTotalOccurrences(50);
      details.setUniqueSessions(40);
      details.setSessionPct(80.0);
      details.setMeanSpanS(2.0);
      details.setMedianSpanS(1.5);
      details.setP95SpanS(5.0);
      details.setCv(0.3);
      details.setEdges(List.of());
      details.setStatus("ACTIVATED");
      details.setCreatedAt(TEST_TIMESTAMP);

      assertThat(details.getId()).isEqualTo(1L);
      assertThat(details.getProjectId()).isEqualTo("proj");
      assertThat(details.getPattern()).containsExactly("EventA");
      assertThat(details.getTotalOccurrences()).isEqualTo(50);
      assertThat(details.getUniqueSessions()).isEqualTo(40);
      assertThat(details.getSessionPct()).isEqualTo(80.0);
      assertThat(details.getMeanSpanS()).isEqualTo(2.0);
      assertThat(details.getMedianSpanS()).isEqualTo(1.5);
      assertThat(details.getP95SpanS()).isEqualTo(5.0);
      assertThat(details.getCv()).isEqualTo(0.3);
      assertThat(details.getEdges()).isEmpty();
      assertThat(details.getStatus()).isEqualTo("ACTIVATED");
      assertThat(details.getCreatedAt()).isEqualTo(TEST_TIMESTAMP);
    }
  }

  @Nested
  class AllArgsConstructor {

    @Test
    void shouldCreateWithAllArgs() {
      List<SuggestedInteractionEdge> edges = List.of(
          SuggestedInteractionEdge.builder().from("X").to("Y").build());

      SuggestedInteractionDetails details = new SuggestedInteractionDetails(
          10L, "project-1", List.of("E1", "E2"), 200, 150, 75.0,
          1.5, 1.2, 4.0, 0.2, edges, "DISMISSED", TEST_TIMESTAMP);

      assertThat(details.getId()).isEqualTo(10L);
      assertThat(details.getProjectId()).isEqualTo("project-1");
      assertThat(details.getPattern()).containsExactly("E1", "E2");
      assertThat(details.getTotalOccurrences()).isEqualTo(200);
      assertThat(details.getUniqueSessions()).isEqualTo(150);
      assertThat(details.getSessionPct()).isEqualTo(75.0);
      assertThat(details.getMeanSpanS()).isEqualTo(1.5);
      assertThat(details.getMedianSpanS()).isEqualTo(1.2);
      assertThat(details.getP95SpanS()).isEqualTo(4.0);
      assertThat(details.getCv()).isEqualTo(0.2);
      assertThat(details.getEdges()).hasSize(1);
      assertThat(details.getStatus()).isEqualTo("DISMISSED");
      assertThat(details.getCreatedAt()).isEqualTo(TEST_TIMESTAMP);
    }
  }

  @Nested
  class EqualsAndHashCode {

    @Test
    void shouldBeEqualForIdenticalObjects() {
      SuggestedInteractionDetails d1 = SuggestedInteractionDetails.builder()
          .id(1L).projectId("p").pattern(List.of("A")).status("PENDING")
          .createdAt(TEST_TIMESTAMP).build();
      SuggestedInteractionDetails d2 = SuggestedInteractionDetails.builder()
          .id(1L).projectId("p").pattern(List.of("A")).status("PENDING")
          .createdAt(TEST_TIMESTAMP).build();

      assertThat(d1).isEqualTo(d2);
      assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
    }

    @Test
    void shouldNotBeEqualForDifferentObjects() {
      SuggestedInteractionDetails d1 = SuggestedInteractionDetails.builder()
          .id(1L).projectId("p1").build();
      SuggestedInteractionDetails d2 = SuggestedInteractionDetails.builder()
          .id(2L).projectId("p2").build();

      assertThat(d1).isNotEqualTo(d2);
    }
  }

  @Nested
  class ToStringTest {

    @Test
    void shouldReturnNonNullString() {
      SuggestedInteractionDetails details = SuggestedInteractionDetails.builder()
          .id(1L).projectId("test").status("PENDING").build();

      assertThat(details.toString()).isNotNull();
      assertThat(details.toString()).contains("test");
    }
  }
}
