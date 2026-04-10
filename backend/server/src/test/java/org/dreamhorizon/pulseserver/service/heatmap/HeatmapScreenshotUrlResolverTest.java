package org.dreamhorizon.pulseserver.service.heatmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.config.HeatmapS3Config;
import org.dreamhorizon.pulseserver.config.SessionReplayS3Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
class HeatmapScreenshotUrlResolverTest {

  @Mock private S3AsyncClient s3Client;

  @Test
  void sanitizePathSegmentMatchesIngestionRules() {
    assertThat(HeatmapScreenshotUrlResolver.sanitizePathSegment("  MainActivity  ", "fb"))
        .isEqualTo("MainActivity");
    assertThat(HeatmapScreenshotUrlResolver.sanitizePathSegment("a/b", "fb")).isEqualTo("a_b");
    assertThat(HeatmapScreenshotUrlResolver.sanitizePathSegment("..hidden", "fb")).isEqualTo("hidden");
    assertThat(HeatmapScreenshotUrlResolver.sanitizePathSegment("   ", "fb")).isEqualTo("fb");
  }

  @Test
  void returnsEmptyWhenBucketMissing() {
    ApplicationConfig cfg = new ApplicationConfig();
    cfg.setSessionReplayS3(new SessionReplayS3Config("", null, null, null, null));
    HeatmapScreenshotUrlResolver resolver = new HeatmapScreenshotUrlResolver(s3Client, cfg);

    assertThat(
            resolver.resolveForScreen(
                "p", "s", "2026-04-08", "2026-04-08", "1.0", "Android", "Mobile_Small"))
        .isEmpty();
  }

  @Test
  void returnsEmptyWhenAppVersionMissing() {
    ApplicationConfig cfg = new ApplicationConfig();
    cfg.setSessionReplayS3(new SessionReplayS3Config("b", "http://minio:9000", "us-east-1", "k", "s"));
    HeatmapScreenshotUrlResolver resolver = new HeatmapScreenshotUrlResolver(s3Client, cfg);

    assertThat(
            resolver.resolveForScreen(
                "p", "s", "2026-04-08", "2026-04-08", null, "Android", "Mobile_Small"))
        .isEmpty();
  }

  @Test
  void usesPublicBaseUrlWhenSet() {
    ApplicationConfig cfg = new ApplicationConfig();
    cfg.setHeatmapS3(new HeatmapS3Config("heatmap-assets", null, null, null, null));
    cfg.setSessionReplayS3(
        new SessionReplayS3Config("session-recordings", "http://minio:9000", "us-east-1", "k", "s"));
    cfg.setHeatmapScreenshotsPublicBaseUrl("https://cdn.example/bucket");

    S3Object o1 =
        S3Object.builder()
            .key("heatmap-screenshots/p/20260408/s/Android/1.0/Mobile_Small/capture-1.json")
            .lastModified(Instant.parse("2026-04-08T12:00:00Z"))
            .build();
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                ListObjectsV2Response.builder().contents(o1).isTruncated(false).build()));

    HeatmapScreenshotUrlResolver resolver = new HeatmapScreenshotUrlResolver(s3Client, cfg);
    List<String> urls =
        resolver.resolveForScreen("p", "s", "2026-04-08", "2026-04-08", "1.0", "Android", "Mobile_Small");

    assertThat(urls)
        .containsExactly(
            "https://cdn.example/bucket/heatmap-screenshots/p/20260408/s/Android/1.0/Mobile_Small/capture-1.json");
  }
}
