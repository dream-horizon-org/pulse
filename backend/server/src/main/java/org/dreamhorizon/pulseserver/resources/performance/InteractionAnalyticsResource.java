package org.dreamhorizon.pulseserver.resources.performance;

import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletionStage;
import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.*;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.interaction.PerformanceMetricService;

@Path("/v1/interactions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InteractionAnalyticsResource {

    private final PerformanceMetricService performanceMetricService;

    @Inject
    public InteractionAnalyticsResource(PerformanceMetricService performanceMetricService) {
        this.performanceMetricService = performanceMetricService;
    }

    @POST
    @Path("/health")
    public CompletionStage<Response<InteractionHealthRes>> getInteractionHealth(InteractionHealthReq req) {
        req.setProjectId(ProjectContext.getProjectId());
        
        if (req.getTopN() == null) {
            req.setTopN(10);
        }
        
        if (req.getOrderBy() == null || req.getOrderBy().isEmpty()) {
            req.setOrderBy(List.of(InteractionOrderBy.builder().field(ClickhouseConstants.ALIAS_SPAN_FREQ).direction("DESC").build()));
        }

        return performanceMetricService.getInteractionHealth(req)
                .map(Response::successfulResponse)
                .toCompletionStage();
    }

    @POST
    @Path("/metrics")
    public CompletionStage<Response<InteractionMetricsRes>> getInteractionMetrics(InteractionMetricsReq req) {
        req.setProjectId(ProjectContext.getProjectId());
        
        if (req.getOrderBy() != null && !req.getOrderBy().isEmpty()) {
            return Single.<Response<InteractionMetricsRes>>error(
                new IllegalArgumentException("orderBy is not supported for metrics endpoint")
            ).toCompletionStage();
        }

        return performanceMetricService.getInteractionMetrics(req)
                .map(Response::successfulResponse)
                .toCompletionStage();
    }

    @POST
    @Path("/breakdown")
    public CompletionStage<Response<InteractionBreakdownRes>> getInteractionBreakdown(InteractionBreakdownReq req) {
        req.setProjectId(ProjectContext.getProjectId());
        
        if (req.getLimit() == null) {
            req.setLimit(10);
        }

        return performanceMetricService.getInteractionBreakdown(req)
                .map(Response::successfulResponse)
                .toCompletionStage();
    }

    @POST
    @Path("/sessions")
    public CompletionStage<Response<InteractionSessionsRes>> getInteractionSessions(InteractionSessionsReq req) {
        req.setProjectId(ProjectContext.getProjectId());
        
        if (req.getLimit() == null) {
            req.setLimit(10);
        }
        
        if (req.getOrderBy() == null || req.getOrderBy().isEmpty()) {
            req.setOrderBy(List.of(InteractionOrderBy.builder().field("timestamp").direction("DESC").build()));
        }

        return performanceMetricService.getInteractionSessions(req)
                .map(Response::successfulResponse)
                .toCompletionStage();
    }
}
