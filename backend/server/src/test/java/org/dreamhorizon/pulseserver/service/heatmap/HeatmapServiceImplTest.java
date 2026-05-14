package org.dreamhorizon.pulseserver.service.heatmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapClickHouseRowDto;
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
  @Mock private HeatmapScreenshotUrlResolver heatmapScreenshotUrlResolver;

  @InjectMocks private HeatmapServiceImpl heatmapService;

  @BeforeEach
  void setProject() {
    ProjectContext.setProjectId(PROJECT);
    lenient()
        .when(
            heatmapScreenshotUrlResolver.resolveForScreen(
                any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
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

    @Test
    void shouldRejectWhenScreenNameBlank() {
      heatmapService
          .getHeatmapData("  ", from, to, null, null, null, null)
          .test()
          .assertError(
              t -> {
                assertThat(t).isInstanceOf(WebApplicationException.class);
                assertThat(((WebApplicationException) t).getResponse().getStatus()).isEqualTo(400);
                return true;
              });
    }

    @Test
    void shouldRejectWhenFromOrToMissing() {
      heatmapService
          .getHeatmapData(screen, null, to, null, null, null, null)
          .test()
          .assertError(WebApplicationException.class);

      heatmapService
          .getHeatmapData(screen, from, null, null, null, null, null)
          .test()
          .assertError(WebApplicationException.class);
    }

    @Test
    void shouldRejectWhenFromToNotIso8601() {
      heatmapService
          .getHeatmapData(screen, "not-a-date", to, null, null, null, null)
          .test()
          .assertError(
              t -> {
                assertThat(t).isInstanceOf(WebApplicationException.class);
                assertThat(((WebApplicationException) t).getResponse().getStatus()).isEqualTo(400);
                return true;
              });
    }

    @Test
    void shouldRejectWhenFeatureListEmpty() {
      PulseConfig cfg =
          PulseConfig.builder().description("d").features(Collections.emptyList()).build();

      when(configService.getActiveSdkConfig(PROJECT)).thenReturn(Single.just(cfg));

      heatmapService
          .getHeatmapData(screen, from, to, null, null, null, null)
          .test()
          .assertError(
              t -> {
                assertThat(t).isInstanceOf(WebApplicationException.class);
                assertThat(((WebApplicationException) t).getResponse().getStatus()).isEqualTo(403);
                return true;
              });
    }

    @Test
    void shouldMapHeatmapRowsIntoGlowAndFrustrationLayers() {
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

      HeatmapClickHouseRowDto row =
          HeatmapClickHouseRowDto.builder()
              .xBin(0.25)
              .yBin(0.75)
              .weightNormal(10L)
              .weightRage(2L)
              .weightDead(1L)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any(), any()))
          .thenAnswer(
              invocation -> {
                Class<?> rowClass = invocation.getArgument(1);
                if (rowClass == HeatmapClickHouseRowDto.class) {
                  return Single.just(
                      QueryResultResponse.<HeatmapClickHouseRowDto>builder()
                          .rows(List.of(row))
                          .build());
                }
                return Single.just(QueryResultResponse.builder().rows(Collections.emptyList()).build());
              });

      heatmapService
          .getHeatmapData(screen, from, to, "1.0", null, null, null)
          .test()
          .assertComplete()
          .assertValue(
              resp -> {
                assertThat(resp.getMetadata().getTotalEvents()).isEqualTo(10L);
                assertThat(resp.getLayers().getGlowMap()).hasSize(1);
                assertThat(resp.getLayers().getGlowMap().get(0).getX()).isEqualTo(0.25);
                assertThat(resp.getLayers().getGlowMap().get(0).getWeight()).isEqualTo(10L);
                assertThat(resp.getLayers().getFrustrationMap().getRageTaps()).hasSize(1);
                assertThat(resp.getLayers().getFrustrationMap().getDeadTaps()).hasSize(1);
                return true;
              });
    }

    @Test
    void shouldSkipBinsWithNullCoordinates() {
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

      HeatmapClickHouseRowDto skip =
          HeatmapClickHouseRowDto.builder()
              .xBin(null)
              .yBin(0.5)
              .weightNormal(99L)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any(), any()))
          .thenAnswer(
              invocation -> {
                Class<?> rowClass = invocation.getArgument(1);
                if (rowClass == HeatmapClickHouseRowDto.class) {
                  return Single.just(
                      QueryResultResponse.<HeatmapClickHouseRowDto>builder()
                          .rows(List.of(skip))
                          .build());
                }
                return Single.just(QueryResultResponse.builder().rows(Collections.emptyList()).build());
              });

      heatmapService
          .getHeatmapData(screen, from, to, "1.0", null, null, null)
          .test()
          .assertComplete()
          .assertValue(
              resp ->
                  resp.getLayers().getGlowMap().isEmpty()
                      && resp.getLayers().getFrustrationMap().getRageTaps().isEmpty()
                      && resp.getLayers().getFrustrationMap().getDeadTaps().isEmpty()
                      && resp.getMetadata().getTotalEvents() == 99L);
    }
  }

  @Nested
  class WithoutProjectContext {

    @BeforeEach
    void clearProjectOnly() {
      ProjectContext.clear();
    }

    @Test
    void shouldThrowWhenProjectIdMissing() {
      assertThatThrownBy(
              () ->
                  heatmapService
                      .getHeatmapData(
                          "HomeScreen", "2026-03-01T00:00:00Z", "2026-03-02T00:00:00Z",
                          null, null, null, null)
                      .blockingGet())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Project context");
    }
  }

  @Nested
  class ScrollFoldClassification {

    private PulseConfig enabledConfig() {
      return PulseConfig.builder()
          .description("d")
          .features(
              List.of(
                  PulseConfig.FeatureConfig.builder()
                      .featureName(Features.heatmap)
                      .sessionSampleRate(1.0)
                      .sdks(List.of(Sdk.pulse_android_java))
                      .build()))
          .build();
    }

    private void stubRows(List<HeatmapClickHouseRowDto> rows) {
      when(configService.getActiveSdkConfig(PROJECT)).thenReturn(Single.just(enabledConfig()));
      when(interactionDao.getAllActiveAndRunningInteractions(PROJECT))
          .thenReturn(Single.just(Collections.emptyList()));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(), any()))
          .thenAnswer(
              invocation -> {
                Class<?> rowClass = invocation.getArgument(1);
                if (rowClass == HeatmapClickHouseRowDto.class) {
                  return Single.just(
                      QueryResultResponse.<HeatmapClickHouseRowDto>builder().rows(rows).build());
                }
                return Single.just(
                    QueryResultResponse.builder().rows(Collections.emptyList()).build());
              });
    }

    @Test
    void shouldExcludeBelowFoldFromTotalEventsAndGlowMap() {
      var above = HeatmapClickHouseRowDto.builder()
          .xBin(0.1).yBin(0.2).outOfFold(false)
          .weightNormal(5L).weightRage(0L).weightDead(0L).build();
      var below = HeatmapClickHouseRowDto.builder()
          .xBin(0.5).yBin(0.8).outOfFold(true)
          .weightNormal(3L).weightRage(1L).weightDead(0L).build();

      stubRows(List.of(above, below));

      heatmapService.getHeatmapData("S", "2026-03-01T00:00:00Z", "2026-03-02T00:00:00Z",
              "1.0", null, null, null)
          .test().assertComplete()
          .assertValue(resp -> {
            assertThat(resp.getMetadata().getTotalEvents()).isEqualTo(5L);
            assertThat(resp.getLayers().getGlowMap()).hasSize(1);
            assertThat(resp.getLayers().getBelowFoldMetrics().getTotalClicks()).isEqualTo(3L);
            assertThat(resp.getLayers().getBelowFoldMetrics().getRageTaps()).isEqualTo(1L);
            return true;
          });
    }

    @Test
    void shouldReturnZeroTotalEventsWhenAllRowsBelowFold() {
      var below = HeatmapClickHouseRowDto.builder()
          .xBin(0.5).yBin(0.9).outOfFold(true)
          .weightNormal(10L).weightRage(2L).weightDead(1L).build();

      stubRows(List.of(below));

      heatmapService.getHeatmapData("S", "2026-03-01T00:00:00Z", "2026-03-02T00:00:00Z",
              "1.0", null, null, null)
          .test().assertComplete()
          .assertValue(resp -> {
            assertThat(resp.getMetadata().getTotalEvents()).isEqualTo(0L);
            assertThat(resp.getLayers().getGlowMap()).isEmpty();
            assertThat(resp.getLayers().getBelowFoldMetrics().getTotalClicks()).isEqualTo(10L);
            return true;
          });
    }

    @Test
    void shouldTreatScrolledTapAsAboveFoldWhenContentPositionWithinViewport() {
      // SDK computed outOfFold=false: (screen_y + scroll_y) ≤ viewportHeight
      var row = HeatmapClickHouseRowDto.builder()
          .xBin(0.1).yBin(0.2).outOfFold(false)
          .weightNormal(8L).weightRage(0L).weightDead(0L).build();

      stubRows(List.of(row));

      heatmapService.getHeatmapData("S", "2026-03-01T00:00:00Z", "2026-03-02T00:00:00Z",
              "1.0", null, null, null)
          .test().assertComplete()
          .assertValue(resp -> {
            assertThat(resp.getMetadata().getTotalEvents()).isEqualTo(8L);
            assertThat(resp.getLayers().getGlowMap()).hasSize(1);
            assertThat(resp.getLayers().getBelowFoldMetrics().getTotalClicks()).isEqualTo(0L);
            return true;
          });
    }

    @Test
    void shouldTreatNullOutOfFoldAsAboveFold() {
      var row = HeatmapClickHouseRowDto.builder()
          .xBin(0.3).yBin(0.4).outOfFold(null)
          .weightNormal(7L).weightRage(0L).weightDead(0L).build();

      stubRows(List.of(row));

      heatmapService.getHeatmapData("S", "2026-03-01T00:00:00Z", "2026-03-02T00:00:00Z",
              "1.0", null, null, null)
          .test().assertComplete()
          .assertValue(resp -> {
            assertThat(resp.getMetadata().getTotalEvents()).isEqualTo(7L);
            assertThat(resp.getLayers().getGlowMap()).hasSize(1);
            assertThat(resp.getLayers().getBelowFoldMetrics().getTotalClicks()).isEqualTo(0L);
            return true;
          });
    }
  }

  @Nested
  class SortVersionsLatestFirst {

    @Test
    void shouldReturnEmptyForNullOrEmptyInput() {
      assertThat(HeatmapServiceImpl.sortVersionsLatestFirst(null)).isEmpty();
      assertThat(HeatmapServiceImpl.sortVersionsLatestFirst(Collections.emptyList())).isEmpty();
    }

    @Test
    void shouldOrderSemverLatestFirst() {
      assertThat(HeatmapServiceImpl.sortVersionsLatestFirst(List.of("1.0.0", "2.0.0", "1.5.0")))
          .containsExactly("2.0.0", "1.5.0", "1.0.0");
    }

    @Test
    void shouldStripVPrefixAndAppendUnparseableLast() {
      assertThat(HeatmapServiceImpl.sortVersionsLatestFirst(List.of("v1.0.0", "not semver", "2.0.0")))
          .containsExactly("2.0.0", "v1.0.0", "not semver");
    }
  }
}
