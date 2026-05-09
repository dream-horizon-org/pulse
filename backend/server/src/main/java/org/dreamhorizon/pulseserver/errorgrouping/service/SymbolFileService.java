package org.dreamhorizon.pulseserver.errorgrouping.service;


import io.reactivex.rxjava3.core.Single;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata;

@Slf4j
public abstract class SymbolFileService {

  public Single<Boolean> uploadFiles(String projectId,
                                     List<UploadFileData> parsedFiles,
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
    List<Single<Boolean>> uploadTasks = new ArrayList<>();

    for (UploadFileData uploadFileData : parsedFiles) {
      String fileName = uploadFileData.getFileName();
      UploadMetadata metadata = metadataMap.get(fileName);
      if (metadata == null) {
        log.warn("Skipping file '{}': No matching metadata found in JSON payload. projectId={}", 
            fileName, projectId);
        continue;
      }

      uploadTasks.add(uploadFile(fileName, new ByteArrayInputStream(uploadFileData.getFileBytes()), metadata));
    }

    if (uploadTasks.isEmpty()) {
      log.error("No upload tasks created: metadata missing for all files. projectId={}", projectId);
      return Single.error(new IllegalArgumentException("No matching metadata found for uploaded files"));
    }

    return Single.merge(uploadTasks)
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

  @lombok.Value
  public static class UploadFileData {
    String fileName;
    byte[] fileBytes;
  }

  public abstract Single<Boolean> uploadFile(String fileName, InputStream fileInputStream, UploadMetadata metadata);

  public abstract Single<String> readFileAsString(UploadMetadata uploadMetadata);

  public abstract Single<byte[]> readFileAsBytes(UploadMetadata uploadMetadata);
}
