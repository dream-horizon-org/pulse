package org.dreamhorizon.pulseserver.service.interaction.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GetSuggestedInteractionsResponseTest {

  @Nested
  class BuilderAndGetters {

    @Test
    void shouldBuildWithAllFieldsAndReturnCorrectValues() {
      SuggestedInteractionDetails detail = SuggestedInteractionDetails.builder()
          .id(1L).projectId("test").events(List.of(
              Event.builder().name("A").props(List.of()).isBlacklisted(false).build(),
              Event.builder().name("B").props(List.of()).isBlacklisted(false).build()
          )).status("PENDING")
          .createdAt(Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 10, 30)))
          .build();

      GetSuggestedInteractionsResponse response = GetSuggestedInteractionsResponse.builder()
          .suggestions(List.of(detail))
          .totalSuggestions(1)
          .build();

      assertThat(response.getSuggestions()).hasSize(1);
      assertThat(response.getSuggestions().get(0).getId()).isEqualTo(1L);
      assertThat(response.getTotalSuggestions()).isEqualTo(1);
    }

    @Test
    void shouldBuildEmptyResponse() {
      GetSuggestedInteractionsResponse response = GetSuggestedInteractionsResponse.builder()
          .suggestions(List.of())
          .totalSuggestions(0)
          .build();

      assertThat(response.getSuggestions()).isEmpty();
      assertThat(response.getTotalSuggestions()).isZero();
    }
  }

  @Nested
  class SettersAndNoArgConstructor {

    @Test
    void shouldSetAndGetAllFields() {
      GetSuggestedInteractionsResponse response = new GetSuggestedInteractionsResponse();
      response.setSuggestions(List.of());
      response.setTotalSuggestions(5);

      assertThat(response.getSuggestions()).isEmpty();
      assertThat(response.getTotalSuggestions()).isEqualTo(5);
    }
  }

  @Nested
  class AllArgsConstructor {

    @Test
    void shouldCreateWithAllArgs() {
      List<SuggestedInteractionDetails> suggestions = List.of(
          SuggestedInteractionDetails.builder().id(1L).build(),
          SuggestedInteractionDetails.builder().id(2L).build());

      GetSuggestedInteractionsResponse response = new GetSuggestedInteractionsResponse(suggestions, 2);

      assertThat(response.getSuggestions()).hasSize(2);
      assertThat(response.getTotalSuggestions()).isEqualTo(2);
    }
  }

  @Nested
  class EqualsAndHashCode {

    @Test
    void shouldBeEqualForIdenticalObjects() {
      GetSuggestedInteractionsResponse r1 = GetSuggestedInteractionsResponse.builder()
          .suggestions(List.of()).totalSuggestions(0).build();
      GetSuggestedInteractionsResponse r2 = GetSuggestedInteractionsResponse.builder()
          .suggestions(List.of()).totalSuggestions(0).build();

      assertThat(r1).isEqualTo(r2);
      assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void shouldNotBeEqualForDifferentObjects() {
      GetSuggestedInteractionsResponse r1 = GetSuggestedInteractionsResponse.builder()
          .suggestions(List.of()).totalSuggestions(0).build();
      GetSuggestedInteractionsResponse r2 = GetSuggestedInteractionsResponse.builder()
          .suggestions(List.of()).totalSuggestions(5).build();

      assertThat(r1).isNotEqualTo(r2);
    }
  }

  @Nested
  class ToStringTest {

    @Test
    void shouldReturnNonNullString() {
      GetSuggestedInteractionsResponse response = GetSuggestedInteractionsResponse.builder()
          .suggestions(List.of()).totalSuggestions(0).build();

      assertThat(response.toString()).isNotNull();
    }
  }
}
