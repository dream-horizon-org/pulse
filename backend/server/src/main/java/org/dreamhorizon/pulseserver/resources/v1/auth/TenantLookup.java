package org.dreamhorizon.pulseserver.resources.v1.auth;

import com.google.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.TenantLookupResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/auth/tenant")
public class TenantLookup {

  private final TenantDao tenantDao;

  @GET
  @Path("/lookup")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TenantLookupResponseDto>> lookupTenantByHost(
      @HeaderParam("Host") String hostHeader) {
    if (hostHeader == null || hostHeader.isBlank()) {
      throw ServiceError.AUTHENTICATION_BAD_REQUEST.getCustomException("Host header is required");
    }

    String domainName = extractSubdomain(hostHeader);
    if (domainName == null || domainName.isBlank()) {
      throw ServiceError.AUTHENTICATION_BAD_REQUEST.getCustomException("Invalid Host header format");
    }

    return tenantDao.getTenantByDomainName(domainName)
        .map(tenant -> TenantLookupResponseDto.builder()
            .gcpTenantId(tenant.getGcpTenantId())
            .tenantId(tenant.getTenantId())
            .tenantName(tenant.getName())
            .build())
        .switchIfEmpty(
            io.reactivex.rxjava3.core.Single.error(
                ServiceError.NOT_FOUND.getCustomException("Tenant not found for domain: " + domainName)))
        .to(RestResponse.jaxrsRestHandler());
  }

  private String extractSubdomain(String hostHeader) {
    // Assuming hostHeader format is like "subdomain.pulse.com" or "subdomain.pulse.com:port"
    // We want to extract "subdomain"
    // First remove port if present
    String host = hostHeader.contains(":") ? hostHeader.substring(0, hostHeader.indexOf(':')) : hostHeader;
    int dotIndex = host.indexOf('.');
    if (dotIndex > 0) {
      return host.substring(0, dotIndex);
    }
    return host;
  }
}

