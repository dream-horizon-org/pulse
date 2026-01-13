package org.dreamhorizon.pulseserver.resources.configs;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class ConfigCacheFilter implements ContainerResponseFilter {

  private static final String CACHE_CONTROL_HEADER = "Cache-Control";
  private static final String CACHE_CONTROL_VALUE = "max-age=3600";

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {
    String path = requestContext.getUriInfo().getPath();
    
    if (path != null && path.contains("/v1/configs/active")) {
      responseContext.getHeaders().add(CACHE_CONTROL_HEADER, CACHE_CONTROL_VALUE);
    }
  }
}

