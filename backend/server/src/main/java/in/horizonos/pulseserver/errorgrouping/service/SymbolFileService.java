package in.horizonos.pulseserver.errorgrouping.service;


import in.horizonos.pulseserver.errorgrouping.model.UploadMetadata;
import io.reactivex.rxjava3.core.Single;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;

@Slf4j
public abstract class SymbolFileService {

  private static final String FILE_PART_NAME = "fileContent";

  @SneakyThrows
  public Single<Boolean> uploadFiles(List<InputPart> fileParts,
                                     List<UploadMetadata> metadataList) {
    Map<String, UploadMetadata> metadataMap = metadataList.stream()
        .collect(Collectors.toMap(
            UploadMetadata::getFileName,
            m -> m,
            (existing, replacement) -> existing
        ));
    if (fileParts == null || fileParts.isEmpty()) {
      log.warn("Multi-file upload failed: Missing file part(s) named '" + FILE_PART_NAME + "'.");
      return Single.just(false); // 400 Bad Request
    }

    List<Single<Boolean>> uploads = new ArrayList<>();

    for (InputPart inputPart : fileParts) {
      String fileName = getFileNameFromPart(inputPart);
      if (fileName.isEmpty() || fileName.equals("unknown-file")) {
        log.warn("Skipping file part with unknown filename.");
        continue;
      }

      UploadMetadata metadata = metadataMap.get(fileName);
      if (metadata == null) {
        log.warn("Skipping file '" + fileName + "': No matching metadata found in JSON payload.");
        continue;
      }

      try (InputStream fileInputStream = inputPart.getBody(InputStream.class, null)) {
        uploads.add(uploadFile(fileName, fileInputStream, metadata));
      } catch (Exception e) {
        log.error("Failed to save file '" + fileName + "'. Error: " + e.getMessage());
        return Single.just(false);
      }
    }

    return Single.merge(uploads)
        .toList()
        .map(res -> res.stream().allMatch(res1 -> res1 == true));
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

  public abstract Single<Boolean> uploadFile(String fileName, InputStream fileInputStream, UploadMetadata metadata);

  public abstract Single<String> readFileAsString(UploadMetadata uploadMetadata);

  public abstract Single<byte[]> readFileAsBytes(UploadMetadata uploadMetadata);
}
