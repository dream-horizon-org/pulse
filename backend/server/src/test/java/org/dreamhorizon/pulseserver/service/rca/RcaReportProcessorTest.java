package org.dreamhorizon.pulseserver.service.rca;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.time.Instant;
import java.time.LocalDate;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaJobStatus;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaReportJobDao;
import org.dreamhorizon.pulseserver.dao.rcajob.models.RcaReportJob;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.service.ai.impl.AiUpstreamProxyExecutor;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.service.rootcause.RcaRelatedHeatmapsMerger;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.vertx.rxjava3.ext.web.client.WebClient;

@ExtendWith(MockitoExtension.class)
class RcaReportProcessorTest {

  @Mock
  private Vertx vertx;

  @Mock
  private RcaReportJobDao jobDao;

  @Mock
  private RcaReportCacheDao cacheDao;

  @Mock
  private RootCauseService rootCauseService;

  @Mock
  private RcaReportEnrichmentService enrichmentService;

  @Mock
  private WebClient webClient;

  @Test
  @SuppressWarnings("unchecked")
  void shouldDelegateToWorkerPoolOnEnqueue() {
    AiUpstreamProxyExecutor upstream = new AiUpstreamProxyExecutor(webClient, "http://ai-test");

    RcaReportProcessor processor =
        new RcaReportProcessor(
            vertx,
            jobDao,
            cacheDao,
            new ObjectMapper(),
            rootCauseService,
            RootCauseConfig.withDefaults(null),
            new RcaRelatedHeatmapsMerger(new ObjectMapper()),
            enrichmentService,
            upstream);

    RcaReportJob job =
        new RcaReportJob(
            "rca-job-x",
            "p1",
            "ix",
            LocalDate.of(2025, 1, 1),
            RcaJobStatus.PENDING,
            0,
            null,
            null,
            Instant.now(),
            null,
            null,
            null,
            null,
            1);

    processor.enqueueProcess(job, "{\"interactionName\":\"ix\"}", false, "Bearer t", null);

    verify(vertx)
        .executeBlocking(
            ArgumentMatchers.<Callable<Object>>any(),
            eq(false),
            ArgumentMatchers.<Handler<AsyncResult<Object>>>any());
  }
}
