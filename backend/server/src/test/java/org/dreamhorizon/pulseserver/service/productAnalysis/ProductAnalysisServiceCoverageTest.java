package org.dreamhorizon.pulseserver.service.productAnalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.resources.dev.models.DevSparkJobTriggerRequest;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobRequest;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exercises Lombok-generated builders/getters and small pieces of logic for
 * service-layer product-analysis & spark models.
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

  @Nested
  class DevSparkJobTriggerRequestModel {

    @Test
    void shouldBuildAndReadAllFields() {
      DevSparkJobTriggerRequest r = DevSparkJobTriggerRequest.builder()
          .entryPoint("s3://jar")
          .mainClass("com.Main")
          .jobName("job")
          .applicationArguments(List.of("--x", "1"))
          .secretsName("s")
          .awsRegion("us-east-1")
          .mode("daily")
          .s3BucketPrefix("s3://bucket/")
          .build();

      assertThat(r.getEntryPoint()).isEqualTo("s3://jar");
      assertThat(r.getMainClass()).isEqualTo("com.Main");
      assertThat(r.getJobName()).isEqualTo("job");
      assertThat(r.getApplicationArguments()).containsExactly("--x", "1");
      assertThat(r.getSecretsName()).isEqualTo("s");
      assertThat(r.getAwsRegion()).isEqualTo("us-east-1");
      assertThat(r.getMode()).isEqualTo("daily");
      assertThat(r.getS3BucketPrefix()).isEqualTo("s3://bucket/");
    }

    @Test
    void shouldResolveArgumentsFromExplicitListWhenProvided() {
      DevSparkJobTriggerRequest r = DevSparkJobTriggerRequest.builder()
          .entryPoint("jar")
          .mainClass("C")
          .applicationArguments(List.of("x", "y"))
          .secretsName("ignored")
          .build();
      assertThat(r.resolveApplicationArguments()).containsExactly("x", "y");
    }

    @Test
    void shouldResolveArgumentsFromExplicitEmptyListAsIs() {
      DevSparkJobTriggerRequest r = DevSparkJobTriggerRequest.builder()
          .entryPoint("jar")
          .mainClass("C")
          .applicationArguments(List.of())
          .build();
      assertThat(r.resolveApplicationArguments()).isEmpty();
    }

    @Test
    void shouldResolveArgumentsFromStructuredFields() {
      DevSparkJobTriggerRequest r = DevSparkJobTriggerRequest.builder()
          .entryPoint("jar")
          .mainClass("C")
          .secretsName("  sec  ")
          .awsRegion("us-east-1")
          .mode("daily")
          .s3BucketPrefix("s3://x")
          .build();
      assertThat(r.resolveApplicationArguments()).containsExactly(
          "--secrets_name", "sec",
          "--aws_region", "us-east-1",
          "--mode", "daily",
          "--s3_bucket_prefix", "s3://x");
    }

    @Test
    void shouldResolveArgumentsToNullWhenNothingSet() {
      DevSparkJobTriggerRequest r = DevSparkJobTriggerRequest.builder()
          .entryPoint("jar").mainClass("C").build();
      assertThat(r.resolveApplicationArguments()).isNull();
    }

    @Test
    void shouldSupportNoArgsAndSetters() {
      DevSparkJobTriggerRequest r = new DevSparkJobTriggerRequest();
      r.setEntryPoint("jar");
      r.setMainClass("C");
      assertThat(r.getEntryPoint()).isEqualTo("jar");
      assertThat(r.getMainClass()).isEqualTo("C");
      assertThat(r.toString()).isNotNull();
    }

    @Test
    void shouldSupportAllArgsAndEquality() {
      DevSparkJobTriggerRequest a = new DevSparkJobTriggerRequest(
          "jar", "C", "j", List.of(), null, null, null, null);
      DevSparkJobTriggerRequest b = new DevSparkJobTriggerRequest(
          "jar", "C", "j", List.of(), null, null, null, null);
      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
  }
}
