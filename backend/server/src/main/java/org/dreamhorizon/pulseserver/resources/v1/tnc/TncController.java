package org.dreamhorizon.pulseserver.resources.v1.tnc;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.v1.tnc.models.AcceptTncRequest;
import org.dreamhorizon.pulseserver.resources.v1.tnc.models.AcceptTncResponse;
import org.dreamhorizon.pulseserver.resources.v1.tnc.models.TncHistoryResponse;
import org.dreamhorizon.pulseserver.resources.v1.tnc.models.TncStatusResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.tnc.TncService;
import org.dreamhorizon.pulseserver.tenant.TenantContext;

import static org.dreamhorizon.pulseserver.constant.Constants.PERMISSION_CAN_ACCEPT_TNC;
import static org.dreamhorizon.pulseserver.constant.Constants.RESOURCE_TYPE_TENANT;

import org.dreamhorizon.pulseserver.util.JwtUtils;

/**
 * Tenant-facing TnC endpoints.
 * Tenant ID is resolved from the JWT token via TenantFilter.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/tnc")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TncController {

  private final TncService tncService;
  private final OpenFgaService openFgaService;

  @GET
  @Path("/status")
  public CompletionStage<Response<TncStatusResponse>> getStatus() {
    String tenantId = TenantContext.getTenantId();
    log.info("Getting TnC status for tenant: {}", tenantId);

    return tncService.getTncStatus(tenantId)
        .map(result -> {
          var version = result.getVersion();
          return TncStatusResponse.builder()
              .accepted(result.isAccepted())
              .version(version.getVersion())
              .versionId(version.getId())
              .documents(TncStatusResponse.TncDocumentUrls.builder()
                  .tos(tncService.generatePresignedUrl(version.getTosS3Url()))
                  .aup(tncService.generatePresignedUrl(version.getAupS3Url()))
                  .privacyPolicy(tncService.generatePresignedUrl(version.getPrivacyPolicyS3Url()))
                  .build())
              .acceptedBy(result.getAcceptedByEmail())
              .acceptedAt(result.getAcceptedAt())
              .build();
        })
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Path("/accept")
  public CompletionStage<Response<AcceptTncResponse>> acceptTnc(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @HeaderParam("User-Agent") String userAgent,
      @NotNull @Valid AcceptTncRequest request) {

    String tenantId = TenantContext.getTenantId();
    String token = authorization.substring("Bearer ".length());
    String userId = JwtUtils.extractUserId(token);
    String userEmail = JwtUtils.extractEmail(token);

    log.info("TnC acceptance attempt: tenant={}, user={}, version={}", tenantId, userEmail, request.getVersionId());

    return openFgaService.checkPermission(userId, PERMISSION_CAN_ACCEPT_TNC, RESOURCE_TYPE_TENANT, tenantId)
        .flatMap(allowed -> {
          if (!allowed) {
            return io.reactivex.rxjava3.core.Single.error(
                new SecurityException("Only tenant admins can accept Terms & Conditions"));
          }

          return tncService.acceptTnc(tenantId, request.getVersionId(), userEmail, userAgent);
        })
        .map(acceptance -> AcceptTncResponse.builder()
            .status("accepted")
            .tenantId(acceptance.getTenantId())
            .version(String.valueOf(acceptance.getTncVersionId()))
            .acceptedBy(acceptance.getAcceptedByEmail())
            .acceptedAt(acceptance.getAcceptedAt())
            .build())
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/history")
  public CompletionStage<Response<TncHistoryResponse>> getHistory() {
    String tenantId = TenantContext.getTenantId();
    log.info("Getting TnC history for tenant: {}", tenantId);

    return tncService.getAcceptanceHistory(tenantId)
        .toList()
        .map(acceptances -> {
          List<TncHistoryResponse.TncHistoryEntry> entries = acceptances.stream()
              .map(a -> TncHistoryResponse.TncHistoryEntry.builder()
                  .versionId(a.getTncVersionId())
                  .acceptedBy(a.getAcceptedByEmail())
                  .acceptedAt(a.getAcceptedAt())
                  .build())
              .toList();
          return TncHistoryResponse.builder()
              .history(entries)
              .totalCount(entries.size())
              .build();
        })
        .to(RestResponse.jaxrsRestHandler());
  }

}
