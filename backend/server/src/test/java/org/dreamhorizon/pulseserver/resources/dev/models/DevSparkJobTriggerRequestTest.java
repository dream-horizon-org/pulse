package org.dreamhorizon.pulseserver.resources.dev.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DevSparkJobTriggerRequestTest {

  @Test
  void resolveApplicationArguments_prefersExplicitList() {
    List<String> explicit = List.of("--mode", "daily");
    DevSparkJobTriggerRequest req =
        DevSparkJobTriggerRequest.builder()
            .entryPoint("s3://b/j.jar")
            .mainClass("c.Main")
            .applicationArguments(explicit)
            .secretsName("ignored")
            .build();

    assertThat(req.resolveApplicationArguments()).containsExactlyElementsOf(explicit);
  }

  @Test
  void resolveApplicationArguments_buildsFromStructuredFields() {
    DevSparkJobTriggerRequest req =
        DevSparkJobTriggerRequest.builder()
            .entryPoint("s3://b/j.jar")
            .mainClass("c.Main")
            .secretsName("prod/x")
            .awsRegion("ap-south-1")
            .mode("daily")
            .s3BucketPrefix("pulse-otel-")
            .build();

    assertThat(req.resolveApplicationArguments())
        .containsExactly(
            "--secrets_name",
            "prod/x",
            "--aws_region",
            "ap-south-1",
            "--mode",
            "daily",
            "--s3_bucket_prefix",
            "pulse-otel-");
  }

  @Test
  void resolveApplicationArguments_returnsNullWhenNothingSet() {
    DevSparkJobTriggerRequest req =
        DevSparkJobTriggerRequest.builder().entryPoint("s3://b/j.jar").mainClass("c.Main").build();

    assertThat(req.resolveApplicationArguments()).isNull();
  }
}
