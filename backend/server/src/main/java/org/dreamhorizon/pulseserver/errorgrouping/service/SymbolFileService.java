package org.dreamhorizon.pulseserver.errorgrouping.service;


import io.reactivex.rxjava3.core.Single;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;

@Slf4j
public abstract class SymbolFileService {

  private static final String FILE_PART_NAME = "fileContent";

  @SneakyThrows
  public Single<Boolean> uploadFiles(String projectId,
                                     List<InputPart> fileParts,
                                     List<UploadMetadata> metadataList) {
    Map<String, UploadMetadata> metadataMap = metadataList.stream()
        .collect(Collectors.toMap(
            UploadMetadata::getFileName,
            m -> {
              m.setProjectId(projectId);
              return m;
            },
            (existing, replacement) -> existing
        ));
    if (fileParts == null || fileParts.isEmpty()) {
      log.error("Missing file part(s) named '{}'", FILE_PART_NAME);
      return Single.error(new IllegalArgumentException("Missing file part(s) named '" + FILE_PART_NAME + "'"));
    }

    List<Single<Boolean>> uploads = new ArrayList<>();

    for (InputPart inputPart : fileParts) {
      String fileName = getFileNameFromPart(inputPart);
      if (fileName.isEmpty() || fileName.equals("unknown-file")) {
        log.warn("Skipping file part with unknown filename. projectId={}", projectId);
        continue;
      }

      UploadMetadata metadata = metadataMap.get(fileName);
      if (metadata == null) {
        log.warn("Skipping file '{}': No matching metadata found in JSON payload. projectId={}", 
            fileName, projectId);
        continue;
      }

      try (InputStream fileInputStream = inputPart.getBody(InputStream.class, null)) {
        uploads.add(uploadFile(fileName, fileInputStream, metadata));
      } catch (Exception e) {
        log.error("Failed to process file '{}': error={}", fileName, e.getMessage(), e);
        return Single.error(new RuntimeException("Failed to process file '" + fileName + "': " + e.getMessage(), e));
      }
    }

    if (uploads.isEmpty()) {
      log.error("No valid files to upload after processing");
      return Single.error(new IllegalArgumentException("No valid files to upload"));
    }

    return Single.merge(uploads)
        .toList()
        .flatMap(res -> {
          boolean allSuccess = res.stream().allMatch(result -> result == true);
          if (!allSuccess) {
            log.error("One or more file uploads failed: totalFiles={}, successful={}", 
                res.size(), res.stream().mapToInt(b -> b ? 1 : 0).sum());
            return Single.error(new RuntimeException("One or more file uploads failed"));
          }
          return Single.just(true);
        });
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
