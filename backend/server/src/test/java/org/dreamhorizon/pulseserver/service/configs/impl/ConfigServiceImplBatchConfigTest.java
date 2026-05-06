package org.dreamhorizon.pulseserver.service.configs.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.configs.SdkConfigsDao;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.service.configs.UploadConfigDetailService;
import org.dreamhorizon.pulseserver.service.configs.models.BatchProcessorConfig;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigServiceImplBatchConfigTest {

  @Mock private SdkConfigsDao sdkConfigsDao;
  @Mock private UploadConfigDetailService uploadConfigDetailService;
  @Mock private ApplicationConfig applicationConfig;
  @Mock private Vertx vertx;
  @Mock private Context context;

  private ConfigServiceImpl configService;

  @BeforeEach
  void setUp() {
    when(vertx.getOrCreateContext()).thenReturn(context);
    doAnswer(invocation -> {
      Handler<Void> handler = invocation.getArgument(0);
      handler.handle(null);
      return null;
    }).when(context).runOnContext(any(Handler.class));
    configService = new ConfigServiceImpl(vertx, sdkConfigsDao, uploadConfigDetailService, applicationConfig);
  }

  @Nested
  class BatchConfigValidation {

    @Test
    void shouldAcceptValidBatchConfig() {
      // Happy path: valid batch config should not throw
      BatchProcessorConfig.BatchProcessorOption validOption =
          BatchProcessorConfig.BatchProcessorOption.builder()
              .maxExportBatchSize(256)
              .scheduleDelay(3000)
              .build();

      ConfigData configData = ConfigData.builder()
          .description("Test")
          .batchConfig(BatchProcessorConfig.builder()
              .batchLogs(validOption)
              .batchSpans(validOption)
              .build())
          .build();

      PulseConfig mockResponse = PulseConfig.builder().version(1L).build();
      when(sdkConfigsDao.createConfig(anyString(), any(ConfigData.class)))
          .thenReturn(Single.just(mockResponse));

      // Should not throw
      configService.createSdkConfig("project-1", configData).test().assertValue(mockResponse);
    }

    @Test
    void shouldRejectBatchSizeBelow1() {
      BatchProcessorConfig.BatchProcessorOption invalidOption =
          BatchProcessorConfig.BatchProcessorOption.builder()
              .maxExportBatchSize(0)
              .scheduleDelay(3000)
              .build();

      ConfigData configData = ConfigData.builder()
          .description("Test")
          .batchConfig(BatchProcessorConfig.builder()
              .batchLogs(invalidOption)
              .build())
          .build();

      assertThatThrownBy(() -> configService.createSdkConfig("project-1", configData).blockingGet())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxExportBatchSize must be between 1 and 5000");
    }

    @Test
    void shouldRejectBatchSizeAbove5000() {
      BatchProcessorConfig.BatchProcessorOption invalidOption =
          BatchProcessorConfig.BatchProcessorOption.builder()
              .maxExportBatchSize(5001)
              .scheduleDelay(3000)
              .build();

      ConfigData configData = ConfigData.builder()
          .description("Test")
          .batchConfig(BatchProcessorConfig.builder()
              .batchLogs(invalidOption)
              .build())
          .build();

      assertThatThrownBy(() -> configService.createSdkConfig("project-1", configData).blockingGet())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxExportBatchSize must be between 1 and 5000");
    }

    @Test
    void shouldRejectScheduleDelayBelow100Ms() {
      BatchProcessorConfig.BatchProcessorOption invalidOption =
          BatchProcessorConfig.BatchProcessorOption.builder()
              .maxExportBatchSize(512)
              .scheduleDelay(99)
              .build();

      ConfigData configData = ConfigData.builder()
          .description("Test")
          .batchConfig(BatchProcessorConfig.builder()
              .batchLogs(invalidOption)
              .build())
          .build();

      assertThatThrownBy(() -> configService.createSdkConfig("project-1", configData).blockingGet())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("scheduleDelay must be between 100 and 60000 milliseconds");
    }

    @Test
    void shouldRejectScheduleDelayAbove60000Ms() {
      BatchProcessorConfig.BatchProcessorOption invalidOption =
          BatchProcessorConfig.BatchProcessorOption.builder()
              .maxExportBatchSize(512)
              .scheduleDelay(60001)
              .build();

      ConfigData configData = ConfigData.builder()
          .description("Test")
          .batchConfig(BatchProcessorConfig.builder()
              .batchSpans(invalidOption)
              .build())
          .build();

      assertThatThrownBy(() -> configService.createSdkConfig("project-1", configData).blockingGet())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("scheduleDelay must be between 100 and 60000 milliseconds");
    }

    @Test
    void shouldAcceptNullBatchConfig() {
      // Backward compat: null batch config should be accepted
      ConfigData configData = ConfigData.builder()
          .description("Test")
          .batchConfig(null)
          .build();

      PulseConfig mockResponse = PulseConfig.builder().version(1L).build();
      when(sdkConfigsDao.createConfig(anyString(), any(ConfigData.class)))
          .thenReturn(Single.just(mockResponse));

      // Should not throw
      configService.createSdkConfig("project-1", configData).test().assertValue(mockResponse);
    }

    @Test
    void shouldAcceptNullBatchOptions() {
      // Backward compat: null individual batch options should be accepted
      ConfigData configData = ConfigData.builder()
          .description("Test")
          .batchConfig(BatchProcessorConfig.builder()
              .batchLogs(null)
              .batchSpans(null)
              .build())
          .build();

      PulseConfig mockResponse = PulseConfig.builder().version(1L).build();
      when(sdkConfigsDao.createConfig(anyString(), any(ConfigData.class)))
          .thenReturn(Single.just(mockResponse));

      // Should not throw
      configService.createSdkConfig("project-1", configData).test().assertValue(mockResponse);
    }
  }
}
