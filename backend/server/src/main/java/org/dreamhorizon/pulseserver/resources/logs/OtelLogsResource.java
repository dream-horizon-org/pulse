package org.dreamhorizon.pulseserver.resources.logs;

import com.google.inject.Inject;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.dreamhorizon.pulseserver.errorgrouping.service.ErrorGroupingService;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;


@Path("/v1/logs")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class OtelLogsResource {

  private final ErrorGroupingService groupingService;

  @POST
  @Consumes("application/x-protobuf")
  @Produces("application/x-protobuf")
  @SneakyThrows
  public CompletionStage<Response> export(@Context HttpHeaders headers, InputStream bodyStream) {
    byte[] rawBody = readAll(maybeGunzip(headers, bodyStream));
    ExportLogsServiceRequest request = ExportLogsServiceRequest.parseFrom(rawBody);
    return groupingService.ingest(request)
        .map(ingestedRecords -> buildSuccessResponse()).onErrorReturn(e -> {
          com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
              // Note: OTLP/HTTP doesn't require Status.code; message SHOULD be developer-facing.
              .setMessage("Bad request: " + sanitize(e.getMessage()))
              .build();
          byte[] statusBytes = status.toByteArray();
          if (acceptsGzip(headers)) {
            statusBytes = gzip(statusBytes);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(statusBytes)
                .header("Content-Type", "application/x-protobuf")
                .header("Content-Encoding", "gzip")
                .build();
          }
          return Response.status(Response.Status.BAD_REQUEST)
              .entity(statusBytes)
              .header("Content-Type", "application/x-protobuf")
              .build();
        }).to(RestResponse.toCompletion());
  }

  @SneakyThrows
  private Response buildSuccessResponse() {
    return Response.ok()
        .header("Content-Type", "application/x-protobuf")
        .build();
  }

  // ---- Helpers ----

  private static boolean acceptsGzip(HttpHeaders headers) {
    List<String> ae = headers.getRequestHeaders().get("Accept-Encoding");
    if (ae == null) {
      return false;
    }
    for (String v : ae) {
      if (v != null && v.toLowerCase().contains("gzip")) {
        return true;
      }
    }
    return false;
  }

  private static InputStream maybeGunzip(HttpHeaders headers, InputStream in) throws IOException {
    List<String> ce = headers.getRequestHeaders().get("Content-Encoding");
    if (ce != null) {
      for (String v : ce) {
        if (v != null && v.toLowerCase().contains("gzip")) {
          return new GZIPInputStream(in);
        }
      }
    }
    return in;
  }

  private static byte[] readAll(InputStream in) throws IOException {
    try (in; ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      byte[] buf = new byte[8192];
      int read;
      while ((read = in.read(buf)) != -1) {
        baos.write(buf, 0, read);
      }
      return baos.toByteArray();
    }
  }

  private static byte[] gzip(byte[] data) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream gos = new GZIPOutputStream(baos)) {
      gos.write(data);
    }
    return baos.toByteArray();
  }

  private static String sanitize(String m) {
    return m == null ? "" : m.replaceAll("\n", " ").replaceAll("\r", " ");
  }
}