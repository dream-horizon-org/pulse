package in.horizonos.pulseserver.resources.symbolicate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import in.horizonos.pulseserver.errorgrouping.model.UploadMetadata;
import in.horizonos.pulseserver.errorgrouping.service.SymbolFileService;
import in.horizonos.pulseserver.rest.io.Response;
import in.horizonos.pulseserver.rest.io.RestResponse;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;


@Path("/v1/symbolicate")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class MappingFileUpload {
  private static final String FILE_PART_NAME = "fileContent";
  private static final String METADATA_PART_NAME = "metadata";
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final SymbolFileService symbolFileService;

  @POST
  @Path("/file/upload")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public CompletionStage<Response<Boolean>> uploadFile(MultipartFormDataInput multipartInput) {
    return Single.defer(() -> {
      Map<String, List<InputPart>> formPartsMap = multipartInput.getFormDataMap();
      try {
        List<InputPart> metadataParts = formPartsMap.get(METADATA_PART_NAME);
        String metadataJson = metadataParts.get(0).getBody(String.class, null);

        List<UploadMetadata> metadataList = objectMapper.readValue(metadataJson, new TypeReference<>() {
        });
        if (metadataList.isEmpty()) {
          log.warn("Metadata part found but contained no valid entries.");
          return Single.just(false);
        }

        List<InputPart> fileParts = formPartsMap.get(FILE_PART_NAME);
        return symbolFileService.uploadFiles(fileParts, metadataList);
      } catch (Exception e) {
        log.error("Multi-file upload failed during processing: " + e.getMessage());
        return Single.just(false);
      }
    }).to(RestResponse.jaxrsRestHandler());
  }


}