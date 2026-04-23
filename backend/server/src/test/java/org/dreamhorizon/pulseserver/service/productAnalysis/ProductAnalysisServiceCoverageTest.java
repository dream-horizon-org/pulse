package org.dreamhorizon.pulseserver.service.productAnalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobRequest;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exercises Lombok-generated builders/getters and small pieces of logic for
 * service-layer product-analysis helpers and Spark job DTOs.
 */
class ProductAnalysisServiceCoverageTest {

  @Nested
  class AnalysisEntityTagsUtil {

    @Test
    void shouldReturnEmptyWhenInputNullOrEmpty() {
      assertThat(AnalysisEntityTags.normalizeOrThrow(null)).isEmpty();
      assertThat(AnalysisEntityTags.normalizeOrThrow(Collections.emptyList())).isEmpty();
    }

    @Test
    void shouldTrimDedupeAndSkipBlanks() {
      List<String> out = AnalysisEntityTags.normalizeOrThrow(Arrays.asList(
          "  alpha  ", "beta", "alpha", null, "", "   ", "gamma"));
      assertThat(out).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void shouldRejectTooLongTag() {
      String longTag = "a".repeat(129);
      assertThatThrownBy(() -> AnalysisEntityTags.normalizeOrThrow(List.of(longTag)))
          .hasMessageContaining("at most");
    }

    @Test
    void shouldRejectTooManyTags() {
      String[] tags = new String[65];
      for (int i = 0; i < 65; i++) {
        tags[i] = "tag" + i;
      }
      assertThatThrownBy(() -> AnalysisEntityTags.normalizeOrThrow(Arrays.asList(tags)))
          .hasMessageContaining("64");
    }
  }

  @Nested
  class SparkJobRequestModel {

    @Test
    void shouldBuildAndReadAllFields() {
      SparkJobRequest req = SparkJobRequest.builder()
          .jobName("job")
          .entryPoint("s3://bucket/app.jar")
          .mainClass("com.example.Main")
          .arguments(List.of("a", "b"))
          .sparkSubmitParameters("--conf k=v")
          .timeoutMinutes(30L)
          .tags(Map.of("env", "dev"))
          .build();

      assertThat(req.getJobName()).isEqualTo("job");
      assertThat(req.getEntryPoint()).isEqualTo("s3://bucket/app.jar");
      assertThat(req.getMainClass()).isEqualTo("com.example.Main");
      assertThat(req.getArguments()).containsExactly("a", "b");
      assertThat(req.getSparkSubmitParameters()).isEqualTo("--conf k=v");
      assertThat(req.getTimeoutMinutes()).isEqualTo(30L);
      assertThat(req.getTags()).containsEntry("env", "dev");
    }
  }

  @Nested
  class SparkJobResponseModel {

    @Test
    void shouldBuildAndReadAllFields() {
      SparkJobResponse resp = SparkJobResponse.builder()
          .applicationId("app")
          .jobRunId("run")
          .arn("arn:xxx")
          .jobName("job")
          .entryPoint("s3://x")
          .mainClass("C")
          .submittedAt("2024-01-01T00:00:00Z")
          .build();

      assertThat(resp.getApplicationId()).isEqualTo("app");
      assertThat(resp.getJobRunId()).isEqualTo("run");
      assertThat(resp.getArn()).isEqualTo("arn:xxx");
      assertThat(resp.getJobName()).isEqualTo("job");
      assertThat(resp.getEntryPoint()).isEqualTo("s3://x");
      assertThat(resp.getMainClass()).isEqualTo("C");
      assertThat(resp.getSubmittedAt()).isEqualTo("2024-01-01T00:00:00Z");
    }
  }
}
