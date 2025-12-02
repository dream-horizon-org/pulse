package org.dreamhorizon.pulseserver.resources;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class CorsFilter implements ContainerResponseFilter {

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
    MultivaluedMap<String, Object> headers = responseContext.getHeaders();
    headers.add("Access-Control-Allow-Origin", "*"); // If you want to be more restrictive it could be localhost:4200
    headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD"); // You can add HEAD, DELETE, TRACE, PATCH
    headers.add("Access-Control-Allow-Headers", "*"); // You can add many more

    if (requestContext.getMethod().equals("OPTIONS")) {
      responseContext.setStatus(200);
    }
  }
}
