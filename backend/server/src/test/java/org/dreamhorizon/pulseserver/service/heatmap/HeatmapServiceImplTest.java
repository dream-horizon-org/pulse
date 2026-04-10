package org.dreamhorizon.pulseserver.service.heatmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.WebApplicationException;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.interaction.InteractionDao;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.models.Features;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeatmapServiceImplTest {

  private static final String PROJECT = "test-proj";

  @Mock private ConfigService configService;
  @Mock private ClickhouseQueryService clickhouseQueryService;
  @Mock private InteractionDao interactionDao;

  @InjectMocks private HeatmapServiceImpl heatmapService;

  @BeforeEach
  void setProject() {
    ProjectContext.setProjectId(PROJECT);
  }

  @AfterEach
  void clearProject() {
    ProjectContext.clear();
  }

  @Nested
  class GetHeatmapData {

    private final String from = "2026-03-01T00:00:00Z";
    private final String to = "2026-03-02T00:00:00Z";
    private final String screen = "HomeScreen";

    @Test
    void shouldRejectWhenHeatmapFeatureDisabled() {
      PulseConfig cfg =
          PulseConfig.builder()
              .description("d")
              .features(
                  List.of(
                      PulseConfig.FeatureConfig.builder()
                          .featureName(Features.heatmap)
                          .sessionSampleRate(0.0)
                          .sdks(Collections.emptyList())
                          .build()))
              .build();

      when(configService.getActiveSdkConfig(PROJECT)).thenReturn(Single.just(cfg));

      var obs =
          heatmapService
              .getHeatmapData(screen, from, to, null, null, null, null)
              .test();

      obs.assertError(
          t -> {
            assertThat(t).isInstanceOf(WebApplicationException.class);
            assertThat(((WebApplicationException) t).getResponse().getStatus()).isEqualTo(403);
            return true;
          });
    }

    @Test
    void shouldRejectWhenHeatmapFeatureMissing() {
      PulseConfig cfg =
          PulseConfig.builder()
              .description("d")
              .features(
                  List.of(
                      PulseConfig.FeatureConfig.builder()
                          .featureName(Features.click)
                          .sessionSampleRate(1.0)
                          .sdks(List.of(Sdk.pulse_android_java))
                          .build()))
              .build();

      when(configService.getActiveSdkConfig(PROJECT)).thenReturn(Single.just(cfg));

      heatmapService
          .getHeatmapData(screen, from, to, null, null, null, null)
          .test()
          .assertError(WebApplicationException.class);
    }

    @Test
    void shouldRunQueryWhenHeatmapEnabled() {
      PulseConfig cfg =
          PulseConfig.builder()
              .description("d")
              .features(
                  List.of(
                      PulseConfig.FeatureConfig.builder()
                          .featureName(Features.heatmap)
                          .sessionSampleRate(1.0)
                          .sdks(List.of(Sdk.pulse_android_java))
                          .build()))
              .build();

      when(configService.getActiveSdkConfig(PROJECT)).thenReturn(Single.just(cfg));
      when(interactionDao.getAllActiveAndRunningInteractions(PROJECT))
          .thenReturn(Single.just(Collections.emptyList()));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(), any()))
          .thenReturn(
              Single.just(QueryResultResponse.builder().rows(Collections.emptyList()).build()));

      heatmapService
          .getHeatmapData(screen, from, to, null, null, null, null)
          .test()
          .assertComplete()
          .assertValue(
              resp ->
                  resp.getMetadata() != null
                      && screen.equals(resp.getMetadata().getScreenName()));
    }
  }
}
