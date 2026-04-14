package org.dreamhorizon.pulseserver.service.interaction.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SuggestedInteractionEdgeTest {

  @Nested
  class BuilderAndGetters {

    @Test
    void shouldBuildWithAllFieldsAndReturnCorrectValues() {
      SuggestedInteractionEdge edge = SuggestedInteractionEdge.builder()
          .from("StepOne")
          .to("StepTwo")
          .meanGapS(0.5)
          .medianGapS(0.4)
          .cv(0.1)
          .p5S(0.1)
          .p95S(1.2)
          .build();

      assertThat(edge.getFrom()).isEqualTo("StepOne");
      assertThat(edge.getTo()).isEqualTo("StepTwo");
      assertThat(edge.getMeanGapS()).isEqualTo(0.5);
      assertThat(edge.getMedianGapS()).isEqualTo(0.4);
      assertThat(edge.getCv()).isEqualTo(0.1);
      assertThat(edge.getP5S()).isEqualTo(0.1);
      assertThat(edge.getP95S()).isEqualTo(1.2);
    }
  }

  @Nested
  class SettersAndNoArgConstructor {

    @Test
    void shouldSetAndGetAllFields() {
      SuggestedInteractionEdge edge = new SuggestedInteractionEdge();
      edge.setFrom("A");
      edge.setTo("B");
      edge.setMeanGapS(1.0);
      edge.setMedianGapS(0.9);
      edge.setCv(0.2);
      edge.setP5S(0.05);
      edge.setP95S(2.0);

      assertThat(edge.getFrom()).isEqualTo("A");
      assertThat(edge.getTo()).isEqualTo("B");
      assertThat(edge.getMeanGapS()).isEqualTo(1.0);
      assertThat(edge.getMedianGapS()).isEqualTo(0.9);
      assertThat(edge.getCv()).isEqualTo(0.2);
      assertThat(edge.getP5S()).isEqualTo(0.05);
      assertThat(edge.getP95S()).isEqualTo(2.0);
    }
  }

  @Nested
  class AllArgsConstructor {

    @Test
    void shouldCreateWithAllArgs() {
      SuggestedInteractionEdge edge = new SuggestedInteractionEdge(
          "X", "Y", 0.5, 0.4, 0.1, 0.05, 1.5);

      assertThat(edge.getFrom()).isEqualTo("X");
      assertThat(edge.getTo()).isEqualTo("Y");
      assertThat(edge.getMeanGapS()).isEqualTo(0.5);
      assertThat(edge.getMedianGapS()).isEqualTo(0.4);
      assertThat(edge.getCv()).isEqualTo(0.1);
      assertThat(edge.getP5S()).isEqualTo(0.05);
      assertThat(edge.getP95S()).isEqualTo(1.5);
    }
  }

  @Nested
  class EqualsAndHashCode {

    @Test
    void shouldBeEqualForIdenticalObjects() {
      SuggestedInteractionEdge edge1 = SuggestedInteractionEdge.builder()
          .from("A").to("B").meanGapS(1.0).medianGapS(0.9).cv(0.1).p5S(0.05).p95S(2.0).build();
      SuggestedInteractionEdge edge2 = SuggestedInteractionEdge.builder()
          .from("A").to("B").meanGapS(1.0).medianGapS(0.9).cv(0.1).p5S(0.05).p95S(2.0).build();

      assertThat(edge1).isEqualTo(edge2);
      assertThat(edge1.hashCode()).isEqualTo(edge2.hashCode());
    }

    @Test
    void shouldNotBeEqualForDifferentObjects() {
      SuggestedInteractionEdge edge1 = SuggestedInteractionEdge.builder()
          .from("A").to("B").meanGapS(1.0).build();
      SuggestedInteractionEdge edge2 = SuggestedInteractionEdge.builder()
          .from("C").to("D").meanGapS(2.0).build();

      assertThat(edge1).isNotEqualTo(edge2);
    }
  }

  @Nested
  class ToStringTest {

    @Test
    void shouldReturnNonNullString() {
      SuggestedInteractionEdge edge = SuggestedInteractionEdge.builder()
          .from("A").to("B").build();

      assertThat(edge.toString()).isNotNull();
      assertThat(edge.toString()).contains("A");
      assertThat(edge.toString()).contains("B");
    }
  }

  @Nested
  class JsonDeserialization {

    @Test
    void shouldDeserializeWithSnakeCaseProperties() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      String json = """
          {
            "from": "StepOne",
            "to": "StepTwo",
            "mean_gap_s": 0.5,
            "median_gap_s": 0.4,
            "cv": 0.1,
            "p5_s": 0.05,
            "p95_s": 1.2
          }
          """;

      SuggestedInteractionEdge edge = mapper.readValue(json, SuggestedInteractionEdge.class);

      assertThat(edge.getFrom()).isEqualTo("StepOne");
      assertThat(edge.getTo()).isEqualTo("StepTwo");
      assertThat(edge.getMeanGapS()).isEqualTo(0.5);
      assertThat(edge.getMedianGapS()).isEqualTo(0.4);
      assertThat(edge.getCv()).isEqualTo(0.1);
      assertThat(edge.getP5S()).isEqualTo(0.05);
      assertThat(edge.getP95S()).isEqualTo(1.2);
    }

    @Test
    void shouldIgnoreUnknownProperties() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      String json = """
          {
            "from": "A",
            "to": "B",
            "unknown_field": 999
          }
          """;

      SuggestedInteractionEdge edge = mapper.readValue(json, SuggestedInteractionEdge.class);

      assertThat(edge.getFrom()).isEqualTo("A");
      assertThat(edge.getTo()).isEqualTo("B");
    }
  }
}
