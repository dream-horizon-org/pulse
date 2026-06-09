package org.dreamhorizon.pulseserver.service.interaction.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CreateSuggestedInteractionsModelsTest {

  @Test
  void shouldBuildCreateSuggestedInteractionsRequest() {
    SuggestedInteractionCreateItem item = SuggestedInteractionCreateItem.builder()
        .events(List.of(Event.builder().name("A").build()))
        .totalOccurrences(10)
        .uniqueSessions(8)
        .sessionPct(5.0)
        .meanSpanS(1.0)
        .medianSpanS(0.9)
        .p95SpanS(2.0)
        .cv(0.1)
        .build();

    CreateSuggestedInteractionsRequest request = CreateSuggestedInteractionsRequest.builder()
        .replacePending(true)
        .suggestions(List.of(item))
        .build();

    assertThat(request.isReplacePending()).isTrue();
    assertThat(request.getSuggestions()).hasSize(1);
    assertThat(request.getSuggestions().get(0).getEvents().get(0).getName()).isEqualTo("A");
  }

  @Test
  void shouldBuildCreateSuggestedInteractionsResponse() {
    CreateSuggestedInteractionsResponse response = CreateSuggestedInteractionsResponse.builder()
        .createdCount(3)
        .replacedPendingCount(2)
        .build();

    assertThat(response.getCreatedCount()).isEqualTo(3);
    assertThat(response.getReplacedPendingCount()).isEqualTo(2);
  }

  @Test
  void shouldDefaultReplacePendingToFalse() {
    CreateSuggestedInteractionsRequest request = new CreateSuggestedInteractionsRequest();
    assertThat(request.isReplacePending()).isFalse();
  }
}
