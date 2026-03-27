package org.dreamhorizon.pulseserver.resources.dev;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.dev.models.DevSparkJobTriggerRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.spark.SparkJobService;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobRequest;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobResponse;

/**
 * Temporary dev-only API to submit an EMR Serverless Spark job (artifact + main class only).
 * <p>Revert or replace when batch triggers are wired through product flows.
 */
@Slf4j
@Path("/internal/v1/dev/spark-jobs")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class DevSparkJobResource {

  private static final String DEFAULT_JOB_NAME = "dev-spark-submit";

  private final SparkJobService sparkJobService;

  /**
   * Triggers {@link SparkJobService#submitJob} with {@link DevSparkJobTriggerRequest}.
   *
   * <p>Requires EMR Serverless enabled and valid AWS credentials / IAM on the server process.
   */
  @POST
  @Path("/trigger")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<SparkJobResponse>> trigger(
      @NotNull @Valid DevSparkJobTriggerRequest body) {
    String jobName =
        body.getJobName() != null && !body.getJobName().isBlank()
            ? body.getJobName().trim()
            : DEFAULT_JOB_NAME;

    List<String> applicationArguments = body.resolveApplicationArguments();

    SparkJobRequest request =
        SparkJobRequest.builder()
            .jobName(jobName)
            .entryPoint(body.getEntryPoint().trim())
            .mainClass(body.getMainClass().trim())
            .arguments(applicationArguments)
            .build();

    log.info(
        "[DevSparkJobResource] trigger jobName={} entryPoint={} applicationArgCount={}",
        jobName,
        body.getEntryPoint(),
        applicationArguments != null ? applicationArguments.size() : 0);

    return sparkJobService.submitJob(request).to(RestResponse.jaxrsRestHandler());
  }
}
