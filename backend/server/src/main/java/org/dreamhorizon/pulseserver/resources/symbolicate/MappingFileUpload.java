package org.dreamhorizon.pulseserver.resources.symbolicate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata;
import org.dreamhorizon.pulseserver.errorgrouping.service.SymbolFileService;
import org.dreamhorizon.pulseserver.errorgrouping.service.SymbolFileService.UploadFileData;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.apikey.ProjectApiKeyService;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
@Path("/v1/symbolicate")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class MappingFileUpload {
  private static final String FILE_PART_NAME = "fileContent";
  private static final String METADATA_PART_NAME = "metadata";

  private final ObjectMapper objectMapper;
  private final SymbolFileService symbolFileService;
  private final ProjectApiKeyService projectApiKeyService;

  @POST
  @Path("/file/upload")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public CompletionStage<Response<Boolean>> uploadFile(MultipartFormDataInput multipartInput) {
    return Single.defer(() -> {
      Map<String, List<InputPart>> formPartsMap = multipartInput.getFormDataMap();
      try {
        List<InputPart> metadataParts = formPartsMap.get(METADATA_PART_NAME);
        if (metadataParts == null || metadataParts.isEmpty()) {
          log.warn("Missing metadata part in upload request");
          return Single.just(Response.successfulResponse(false));
        }

        String metadataJson = metadataParts.get(0).getBody(String.class, null);
        if (metadataJson == null || metadataJson.trim().isEmpty()) {
          log.warn("Metadata content is empty");
          return Single.just(Response.successfulResponse(false));
        }

        List<UploadMetadata> metadataList;
        try {
          metadataList = objectMapper.readValue(metadataJson, new TypeReference<>() {});
        } catch (Exception e) {
          log.error("Failed to parse metadata JSON: error={}", e.getMessage());
          return Single.error(
              ServiceError.INVALID_REQUEST_BODY.getCustomException("Invalid metadata JSON: " + e.getMessage(), e.getMessage()));
        }

        if (metadataList.isEmpty()) {
          log.warn("Metadata part found but contained no valid entries.");
          return Single.just(Response.successfulResponse(false));
        }

        List<InputPart> fileParts = formPartsMap.get(FILE_PART_NAME);
        List<UploadFileData> uploadFiles = parseUploadFiles(fileParts);
        String projectId = ProjectContext.requireProjectId();
        return projectApiKeyService.validateProjectExists(projectId)
          .flatMap(exists -> { 
            if (!exists) {
              return Single.error(new RuntimeException("Project not found: " + projectId));
            }
            return symbolFileService.uploadFiles(projectId, uploadFiles, metadataList)
              .map(Response::successfulResponse)
              .onErrorResumeNext(error -> {
                log.error("Upload failed: projectId={}, error={}", projectId, error.getMessage(), error);
                return Single.error(
                    ServiceError.INTERNAL_SERVER_ERROR.getCustomException(error.getMessage(), error.getMessage()));
              });
          });
      } catch (IllegalStateException e) {
        log.error("API key invalid or missing: error={}", e.getMessage());
        return Single.error(
            ServiceError.UNAUTHORISED.getCustomException("Missing or invalid API key", e.getMessage()));
      } catch (Exception e) {
        log.error("Upload processing failed: error={}", e.getMessage(), e);
        return Single.error(
            ServiceError.INTERNAL_SERVER_ERROR.getCustomException(e.getMessage(), e.getMessage()));
      }
    }).to(org.dreamhorizon.pulseserver.util.CompletableFutureUtils::fromSingle);
  }

  private List<UploadFileData> parseUploadFiles(List<InputPart> fileParts) {
    if (fileParts == null || fileParts.isEmpty()) {
      return List.of();
    }

    List<UploadFileData> uploadFiles = new ArrayList<>();
    for (InputPart inputPart : fileParts) {
      String fileName = getFileNameFromPart(inputPart);
      if (fileName.isEmpty() || fileName.equals("unknown-file")) {
        log.warn("Skipping file part with unknown filename during multipart parse");
        continue;
      }

      try (InputStream fileInputStream = inputPart.getBody(InputStream.class, null)) {
        byte[] fileBytes = fileInputStream.readAllBytes();
        uploadFiles.add(new UploadFileData(fileName, fileBytes));
      } catch (Exception e) {
        throw new RuntimeException("Failed to process file '" + fileName + "': " + e.getMessage(), e);
      }
    }
    return uploadFiles;
  }

  private String getFileNameFromPart(InputPart inputPart) {
    String contentDisposition = inputPart.getHeaders().getFirst("Content-Disposition");
    if (contentDisposition != null) {
      String[] tokens = contentDisposition.split(";");
      for (String token : tokens) {
        if (token.trim().startsWith("filename")) {
          return token.substring(token.indexOf('=') + 1).trim().replace("\"", "");
        }
      }
    }
    return "unknown-file";
  }
}
