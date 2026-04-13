package org.dreamhorizon.pulseserver.service.heatmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
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
                "p", "s", "2026-04-08", "2026-04-08",
                List.of("1.0"), "Android", "Mobile_Small"))
        .isEmpty();
  }

  @Test
  void returnsEmptyWhenAppVersionListEmpty() {
    ApplicationConfig cfg = new ApplicationConfig();
    cfg.setSessionReplayS3(new SessionReplayS3Config("b", "http://minio:9000", "us-east-1", "k", "s"));
    HeatmapScreenshotUrlResolver resolver = new HeatmapScreenshotUrlResolver(s3Client, cfg);

    assertThat(
            resolver.resolveForScreen(
                "p", "s", "2026-04-08", "2026-04-08",
                Collections.emptyList(), "Android", "Mobile_Small"))
        .isEmpty();
  }

  @Test
  void returnsEmptyWhenAppVersionListNull() {
    ApplicationConfig cfg = new ApplicationConfig();
    cfg.setSessionReplayS3(new SessionReplayS3Config("b", "http://minio:9000", "us-east-1", "k", "s"));
    HeatmapScreenshotUrlResolver resolver = new HeatmapScreenshotUrlResolver(s3Client, cfg);

    assertThat(
            resolver.resolveForScreen(
                "p", "s", "2026-04-08", "2026-04-08",
                null, "Android", "Mobile_Small"))
        .isEmpty();
  }

  @Test
  void returnsPresignedUrlWhenObjectPresent() {
    ApplicationConfig cfg = new ApplicationConfig();
    cfg.setHeatmapS3(new HeatmapS3Config("heatmap-assets", null, null, null, null, null));
    cfg.setSessionReplayS3(
        new SessionReplayS3Config("session-recordings", "http://minio:9000", "us-east-1", "k", "s"));

    String objectKey = "heatmap-screenshots/p/20260408/s/Android/1.0/Mobile_Small/capture-1.json";
    S3Object o1 =
        S3Object.builder()
            .key(objectKey)
            .lastModified(Instant.parse("2026-04-08T12:00:00Z"))
            .build();
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                ListObjectsV2Response.builder().contents(o1).isTruncated(false).build()));

    HeatmapScreenshotUrlResolver resolver = new HeatmapScreenshotUrlResolver(s3Client, cfg);
    List<String> urls =
        resolver.resolveForScreen(
            "p", "s", "2026-04-08", "2026-04-08",
            List.of("1.0"), "Android", "Mobile_Small");

    assertThat(urls).hasSize(1);
    assertThat(urls.get(0)).contains(objectKey);
    assertThat(urls.get(0)).contains("X-Amz-Algorithm=");
  }

  @Test
  void fallsBackToOlderVersionWhenLatestHasNoScreenshots() {
    ApplicationConfig cfg = new ApplicationConfig();
    cfg.setHeatmapS3(new HeatmapS3Config("heatmap-assets", null, null, null, null, null));
    cfg.setSessionReplayS3(
        new SessionReplayS3Config("session-recordings", "http://minio:9000", "us-east-1", "k", "s"));

    ListObjectsV2Response emptyResponse =
        ListObjectsV2Response.builder().contents(Collections.emptyList())
            .isTruncated(false).build();
    S3Object o1 =
        S3Object.builder()
            .key("heatmap-screenshots/p/20260408/s/Android/1.0/Mobile_Small/capture-1.json")
            .lastModified(Instant.parse("2026-04-08T12:00:00Z"))
            .build();
    ListObjectsV2Response olderVersionResponse =
        ListObjectsV2Response.builder().contents(o1).isTruncated(false).build();

    when(s3Client.listObjectsV2(argThat((ListObjectsV2Request r) ->
            r != null && r.prefix() != null && r.prefix().contains("/2.0/"))))
        .thenReturn(CompletableFuture.completedFuture(emptyResponse));
    when(s3Client.listObjectsV2(argThat((ListObjectsV2Request r) ->
            r != null && r.prefix() != null && r.prefix().contains("/1.0/"))))
        .thenReturn(CompletableFuture.completedFuture(olderVersionResponse));

    HeatmapScreenshotUrlResolver resolver = new HeatmapScreenshotUrlResolver(s3Client, cfg);
    List<String> urls =
        resolver.resolveForScreen(
            "p", "s", "2026-04-08", "2026-04-08",
            List.of("2.0", "1.0"), "Android", "Mobile_Small");

    String objectKey = "heatmap-screenshots/p/20260408/s/Android/1.0/Mobile_Small/capture-1.json";
    assertThat(urls).hasSize(1);
    assertThat(urls.get(0)).contains(objectKey);
    assertThat(urls.get(0)).contains("X-Amz-Algorithm=");
  }
}
