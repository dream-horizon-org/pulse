package org.dreamhorizon.pulseserver.resources.v1.tnc;

import static org.dreamhorizon.pulseserver.constant.Constants.PERMISSION_CAN_UPLOAD_TNC;
import static org.dreamhorizon.pulseserver.constant.Constants.RESOURCE_SYSTEM_PULSE;
import static org.dreamhorizon.pulseserver.constant.Constants.RESOURCE_TYPE_SYSTEM;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.v1.tnc.models.PublishTncResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.tnc.TncService;
import org.dreamhorizon.pulseserver.util.JwtUtils;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

/**
 * Admin-only TnC endpoints for uploading and publishing TnC documents.
 * Only users with the superadmin role on system:pulse can access these endpoints.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/admin/tnc")
@Produces(MediaType.APPLICATION_JSON)
public class TncAdminController {

  private final TncService tncService;
  private final OpenFgaService openFgaService;

  @POST
  @Path("/upload-and-publish")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public CompletionStage<Response<PublishTncResponse>> uploadAndPublish(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @HeaderParam("user-email") String userEmailHeader,
      MultipartFormDataInput multipartInput) {

    String token = authorization.substring("Bearer ".length());
    String emailForAuth = JwtUtils.extractEmail(token);

    return openFgaService.checkPermission(emailForAuth, PERMISSION_CAN_UPLOAD_TNC, RESOURCE_TYPE_SYSTEM, RESOURCE_SYSTEM_PULSE)
        .flatMap(allowed -> {
          if (!allowed) {
            return Single.error(new SecurityException(
                "Forbidden: only superadmins can upload Terms & Conditions"));
          }

          return Single.defer(() -> {
            try {
              Map<String, List<InputPart>> formParts = multipartInput.getFormDataMap();

              String version = extractRequiredString(formParts, "version");
              String summary = extractOptionalString(formParts, "summary");
              String createdBy = emailForAuth != null ? emailForAuth : "admin";

              byte[] tosBytes = extractRequiredFile(formParts, "tos");
              byte[] aupBytes = extractRequiredFile(formParts, "aup");
              byte[] ppBytes = extractRequiredFile(formParts, "privacy_policy");

              log.info("Upload-and-publish TnC version {} by {}: tos={}B, aup={}B, pp={}B",
                  version, createdBy, tosBytes.length, aupBytes.length, ppBytes.length);

              return tncService.uploadAndPublish(version, summary, createdBy, tosBytes, aupBytes, ppBytes)
                  .map(tncVersion -> PublishTncResponse.builder()
                      .versionId(tncVersion.getId())
                      .version(tncVersion.getVersion())
                      .status("active")
                      .message("All tenants will be required to re-accept on next login")
                      .build());
            } catch (Exception e) {
              log.error("Failed to process upload-and-publish: {}", e.getMessage());
              return Single.error(e);
            }
          });
        })
        .to(RestResponse.jaxrsRestHandler());
  }

  private String extractRequiredString(Map<String, List<InputPart>> formParts, String field) throws Exception {
    List<InputPart> parts = formParts.get(field);
    if (parts == null || parts.isEmpty()) {
      throw new IllegalArgumentException("Missing required field: " + field);
    }
    return parts.get(0).getBody(String.class, null).trim();
  }

  private String extractOptionalString(Map<String, List<InputPart>> formParts, String field) {
    try {
      List<InputPart> parts = formParts.get(field);
      if (parts == null || parts.isEmpty()) {
        return null;
      }
      return parts.get(0).getBody(String.class, null).trim();
    } catch (Exception e) {
      return null;
    }
  }

  private byte[] extractRequiredFile(Map<String, List<InputPart>> formParts, String field) throws Exception {
    List<InputPart> parts = formParts.get(field);
    if (parts == null || parts.isEmpty()) {
      throw new IllegalArgumentException("Missing required file: " + field);
    }
    try (InputStream is = parts.get(0).getBody(InputStream.class, null)) {
      return is.readAllBytes();
    }
  }

}
