package org.dreamhorizon.pulseserver.resources.heatmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import jakarta.ws.rs.WebApplicationException;
import java.util.Collections;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapDataRestResponse;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapLayersRestDto;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapMetadataRestDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.heatmap.HeatmapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
class HeatmapControllerTest {

  @Mock private HeatmapService heatmapService;

  private HeatmapController controller;

  @BeforeEach
  void setup() {
    controller = new HeatmapController(heatmapService);
  }

  @Nested
  @MockitoSettings(strictness = Strictness.LENIENT)
  class GetHeatmapData {

    @Test
    void shouldDelegateToHeatmapServiceWithAllQueryParams(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(
          v -> {
            String screen = "Checkout";
            String from = "2026-04-01T00:00:00Z";
            String to = "2026-04-02T00:00:00Z";
            String appVersion = "2.1.0";
            String platform = "iOS";
            String breakpoint = "Mobile_Small";
            String region = "EU";

            HeatmapDataRestResponse data =
                HeatmapDataRestResponse.builder()
                    .metadata(
                        HeatmapMetadataRestDto.builder()
                            .screenName(screen)
                            .totalEvents(42L)
                            .fromDate(from)
                            .toDate(to)
                            .screenshotUrls(Collections.emptyList())
                            .build())
                    .layers(HeatmapLayersRestDto.builder().build())
                    .build();

            when(heatmapService.getHeatmapData(
                    eq(screen),
                    eq(from),
                    eq(to),
                    eq(appVersion),
                    eq(platform),
                    eq(breakpoint),
                    eq(region)))
                .thenReturn(Single.just(data));

            CompletionStage<Response<HeatmapDataRestResponse>> stage =
                controller.getHeatmapData(
                    screen, from, to, appVersion, platform, breakpoint, region);

            stage.whenComplete(
                (resp, err) -> {
                  testContext.verify(
                      () -> {
                        assertNull(err);
                        assertNotNull(resp);
                        assertNotNull(resp.getData());
                        assertThat(resp.getData().getMetadata().getScreenName()).isEqualTo(screen);
                        assertThat(resp.getData().getMetadata().getTotalEvents()).isEqualTo(42L);
                        verify(heatmapService)
                            .getHeatmapData(screen, from, to, appVersion, platform, breakpoint, region);
                      });
                  testContext.completeNow();
                });
          });
    }

    @Test
    void shouldPropagateErrorWhenServiceFails(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(
          v -> {
            when(heatmapService.getHeatmapData(
                    eq("S"), eq("2026-01-01T00:00:00Z"), eq("2026-01-02T00:00:00Z"),
                    eq((String) null), eq((String) null), eq((String) null), eq((String) null)))
                .thenReturn(
                    Single.error(new WebApplicationException("heatmap failed", 500)));

            CompletionStage<Response<HeatmapDataRestResponse>> stage =
                controller.getHeatmapData(
                    "S", "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", null, null, null, null);

            stage.whenComplete(
                (resp, err) -> {
                  testContext.verify(
                      () -> {
                        assertThat(resp).isNull();
                        assertNotNull(err);
                        if (err instanceof WebApplicationException) {
                          assertThat(((WebApplicationException) err).getResponse().getStatus())
                              .isEqualTo(500);
                        } else {
                          assertThat(err.getCause()).isInstanceOf(WebApplicationException.class);
                        }
                      });
                  testContext.completeNow();
                });
          });
    }
  }
}
