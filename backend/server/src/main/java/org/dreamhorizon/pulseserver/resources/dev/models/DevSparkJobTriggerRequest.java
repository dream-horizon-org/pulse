package org.dreamhorizon.pulseserver.resources.dev.models;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Temporary request body for {@code POST /internal/v1/dev/spark-jobs/trigger}. Remove when replaced
 * by a production flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevSparkJobTriggerRequest {

  /** S3 URI of the main JAR (EMR {@code entryPoint}). */
  @NotBlank(message = "entryPoint is required")
  private String entryPoint;

  /** Fully qualified main class name. */
  @NotBlank(message = "mainClass is required")
  private String mainClass;

  /** Optional job name for EMR; defaults to {@code dev-spark-submit}. */
  private String jobName;

  /**
   * Main-class application args (EMR {@code entryPointArguments}), e.g. {@code ["--mode","daily"]}.
   * If non-null, used as-is (including empty list). Otherwise use structured fields below.
   */
  private List<String> applicationArguments;

  /** Passed as {@code --secrets_name &lt;value&gt;} when non-blank (unless {@code applicationArguments} is set). */
  private String secretsName;

  /** Passed as {@code --aws_region &lt;value&gt;} when non-blank. */
  private String awsRegion;

  /** Passed as {@code --mode &lt;value&gt;} when non-blank. */
  private String mode;

  /** Passed as {@code --s3_bucket_prefix &lt;value&gt;} when non-blank. */
  private String s3BucketPrefix;

  /**
   * Resolves Spark {@code entryPointArguments}: {@code applicationArguments} when non-null, else
   * flag/value pairs from structured fields.
   */
  public List<String> resolveApplicationArguments() {
    if (applicationArguments != null) {
      return new ArrayList<>(applicationArguments);
    }
    List<String> args = new ArrayList<>();
    addFlagValue(args, "--secrets_name", secretsName);
    addFlagValue(args, "--aws_region", awsRegion);
    addFlagValue(args, "--mode", mode);
    addFlagValue(args, "--s3_bucket_prefix", s3BucketPrefix);
    return args.isEmpty() ? null : args;
  }

  private static void addFlagValue(List<String> args, String flag, String value) {
    if (value != null && !value.isBlank()) {
      args.add(flag);
      args.add(value.trim());
    }
  }
}
