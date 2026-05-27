package org.dreamhorizon.pulseserver.service.productAnalysis.journey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults.models.JourneyResultRow;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneyDirection;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneyTopPathResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneyTopPathStep;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JourneyMostVisitedPathDeriverTest {

  private static JourneyResultRow edge(
      int posFrom, String eventFrom, int posTo, String eventTo, long userCount) {
    return JourneyResultRow.builder()
        .direction("END")
        .posFrom(posFrom)
        .eventFrom(eventFrom)
        .posTo(posTo)
        .eventTo(eventTo)
        .userCount(userCount)
        .build();
  }

  @Nested
  class EndDirection {

    @Test
    void shouldDeriveDepthThreePathGreedyByTraffic() {
      List<JourneyResultRow> rows =
          List.of(
              edge(-1, "", 0, "order_placed", 5000),
              edge(-1, "Checkout", 0, "order_placed", 3200),
              edge(-1, "WalletPay", 0, "order_placed", 400),
              edge(-2, "Cart", -1, "Checkout", 3500),
              edge(-2, "BuyNow", -1, "Checkout", 800),
              edge(-3, "ProductDetail", -2, "Cart", 4000),
              edge(-3, "Home", -2, "Cart", 600),
              edge(-3, "Search", -2, "ProductDetail", 500));

      JourneyTopPathResponse result =
          JourneyMostVisitedPathDeriver.derive(rows, JourneyDirection.END, "order_placed", 3);

      assertThat(result.isComplete()).isTrue();
      assertThat(result.getAnchorTraffic()).isEqualTo(5000L);
      assertThat(result.getPathTraffic()).isEqualTo(3200L);
      assertThat(result.getSteps())
          .extracting(JourneyTopPathStep::getStepName)
          .containsExactly("ProductDetail", "Cart", "Checkout", "order_placed");
      assertThat(result.getSteps())
          .extracting(JourneyTopPathStep::getPosition)
          .containsExactly(-3, -2, -1, 0);
      assertThat(result.getPathTraffic()).isEqualTo(3200L);
    }

    @Test
    void shouldBreakTiesLexicographically() {
      List<JourneyResultRow> rows =
          List.of(
              edge(-1, "", 0, "order_placed", 100),
              edge(-1, "Beta", 0, "order_placed", 50),
              edge(-1, "Alpha", 0, "order_placed", 50));

      JourneyTopPathResponse result =
          JourneyMostVisitedPathDeriver.derive(rows, JourneyDirection.END, "order_placed", 1);

      assertThat(result.getSteps())
          .extracting(JourneyTopPathStep::getStepName)
          .containsExactly("Alpha", "order_placed");
    }

    @Test
    void shouldReturnIncompleteWhenOnlyAnchorExists() {
      List<JourneyResultRow> rows = List.of(edge(-1, "", 0, "order_placed", 100));

      JourneyTopPathResponse result =
          JourneyMostVisitedPathDeriver.derive(rows, JourneyDirection.END, "order_placed", 3);

      assertThat(result.isComplete()).isFalse();
      assertThat(result.getSteps()).hasSize(1);
      assertThat(result.getIncompletenessReason()).isNotBlank();
    }
  }

  @Nested
  class StartDirection {

    @Test
    void shouldWalkForwardFromAnchor() {
      List<JourneyResultRow> rows =
          List.of(
              edge(-1, "", 0, "App_Launch", 1000),
              edge(0, "App_Launch", 1, "Home", 800),
              edge(0, "App_Launch", 1, "Search", 200),
              edge(1, "Home", 2, "ProductDetail", 600),
              edge(1, "Home", 2, "Profile", 100));

      JourneyTopPathResponse result =
          JourneyMostVisitedPathDeriver.derive(rows, JourneyDirection.START, "App_Launch", 2);

      assertThat(result.isComplete()).isTrue();
      assertThat(result.getAnchorTraffic()).isEqualTo(1000L);
      assertThat(result.getSteps())
          .extracting(JourneyTopPathStep::getStepName)
          .containsExactly("App_Launch", "Home", "ProductDetail");
      assertThat(result.getPathTraffic()).isEqualTo(600L);
    }

    @Test
    void shouldCapStepsToGraphDepthWindow() {
      List<JourneyResultRow> rows =
          List.of(
              edge(-1, "", 0, "App_Launch", 100),
              edge(0, "App_Launch", 1, "Home", 80),
              edge(1, "Home", 2, "ProductDetail", 60),
              edge(2, "ProductDetail", 3, "Checkout", 40),
              edge(3, "Checkout", 4, "Payment", 30),
              edge(4, "Payment", 5, "Confirm", 20),
              edge(5, "Confirm", 6, "Extra", 10));

      JourneyTopPathResponse result =
          JourneyMostVisitedPathDeriver.derive(rows, JourneyDirection.START, "App_Launch", 6);

      assertThat(result.getSteps())
          .extracting(JourneyTopPathStep::getPosition)
          .containsExactly(0, 1, 2, 3, 4);
      assertThat(result.getSteps())
          .extracting(JourneyTopPathStep::getStepName)
          .doesNotContain("Confirm", "Extra");
    }

    @Test
    void shouldUseSumOfOutgoingForStartAnchorTraffic() {
      List<JourneyResultRow> rows =
          List.of(
              edge(-1, "", 0, "App_Launch", 100),
              edge(0, "App_Launch", 1, "Home", 80),
              edge(0, "App_Launch", 1, "Search", 15));

      JourneyTopPathResponse result =
          JourneyMostVisitedPathDeriver.derive(rows, JourneyDirection.START, "App_Launch", 2);

      assertThat(result.getAnchorTraffic()).isEqualTo(95L);
      assertThat(result.getSteps().get(0).getTraffic()).isEqualTo(95L);
    }
  }

  @Test
  void shouldReturnEmptyWhenRowsMissing() {
    JourneyTopPathResponse result =
        JourneyMostVisitedPathDeriver.derive(List.of(), JourneyDirection.END, "order_placed", 3);

    assertThat(result.getSteps()).isEmpty();
    assertThat(result.isComplete()).isFalse();
  }
}
