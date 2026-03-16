package org.dreamhorizon.pulseserver.errorgrouping.service;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata;

@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Slf4j
public class MysqlSymbolFileService extends SymbolFileService {
  private final MysqlClient d11MysqlClient;
  private final S3SymbolFileService s3SymbolFileService;

  @Override
  public Single<Boolean> uploadFile(String fileName, InputStream fileInputStream, UploadMetadata metadata) {
    return Single.fromCallable(() -> {
      byte[] fileBytes = fileInputStream.readAllBytes();
      return fileBytes;
    })
    .flatMap(fileBytes -> {
      return s3SymbolFileService.uploadFile(metadata, new ByteArrayInputStream(fileBytes))
          .flatMap(s3Key -> {
            final String sql =
                "INSERT INTO symbol_files "
                    + "  (app_version, app_version_code, platform, framework, s3_key, bundleid, project_id) "
                    + "VALUES (?,?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE s3_key = VALUES(s3_key), bundleid = VALUES(bundleid)";
            
            return d11MysqlClient.getWriterPool()
                .preparedQuery(sql)
                .execute(Tuple.wrap(Arrays.asList(
                    metadata.getAppVersion(),
                    metadata.getVersionCode(),
                    metadata.getPlatform(),
                    metadata.getType(),
                    s3Key,
                    metadata.getBundleId(),
                    metadata.getProjectId())))
                .map(rows -> {
                  log.info("Symbol file uploaded successfully: metadata={} , s3Key={}", metadata, s3Key);
                  return true;
                })
                .onErrorResumeNext(dbError -> {
                  log.error("Database insert failed: projectId={}, framework={}, platform={}, error={}", 
                      metadata.getProjectId(), metadata.getType(), metadata.getPlatform(), dbError.getMessage(), dbError);
                  return Single.error(new RuntimeException("Database insert failed: " + dbError.getMessage(), dbError));
                });
          })
          .onErrorResumeNext(s3Error -> {
            log.error("S3 upload failed: fileName={}, error={}", fileName, s3Error.getMessage(), s3Error);
            return Single.error(new RuntimeException("S3 upload failed: " + s3Error.getMessage(), s3Error));
          });
    });
  }

  public Single<Buffer> readFile(UploadMetadata metadata) {
    log.info("Fetching symbol file from DATABASE for: {}", metadata);

    final String sql = """
        SELECT s3_key
        FROM symbol_files
        WHERE project_id=? AND app_version=? AND app_version_code=? AND platform=? AND framework=?
        LIMIT 1
        """;

    Tuple params = Tuple.of(
        metadata.getProjectId(),
        metadata.getAppVersion(),
        metadata.getVersionCode(),
        metadata.getPlatform(),
        metadata.getType()
    );

    return d11MysqlClient.getReaderPool()
        .preparedQuery(sql)
        .execute(params)
        .flatMap((RowSet<Row> rows) -> {
          var it = rows.iterator();
          if (!it.hasNext()) {
            log.warn("Symbol file not found: projectId={}, appVersion={}, versionCode={}, platform={}, framework={}",
                metadata.getProjectId(), metadata.getAppVersion(), metadata.getVersionCode(),
                metadata.getPlatform(), metadata.getType());
            return Single.error(new NoSuchElementException("No symbol file found for: " + metadata));
          }
          
          Row row = it.next();
          String s3Key = row.getString(0); // s3_key column
          
          if (s3Key == null || s3Key.trim().isEmpty()) {
            log.error("S3 key is null or empty: metadata={}", metadata);
            return Single.error(new NoSuchElementException("S3 key not found for: " + metadata));
          }
          
          // Download file from S3
          return s3SymbolFileService.downloadFileAsBytes(s3Key)
              .map(Buffer::buffer)
              .onErrorResumeNext(error -> {
                log.error("S3 download failed: s3Key={}, projectId={}, error={}",
                    s3Key, metadata.getProjectId(), error.getMessage(), error);
                return Single.error(error);
              });
        });
  }

  @Override
  public Single<byte[]> readFileAsBytes(UploadMetadata metadata) {
    return readFile(metadata).map(Buffer::getBytes);
  }

  @Override
  public Single<String> readFileAsString(UploadMetadata metadata) {
    return readFile(metadata).map(buffer -> buffer.toString(StandardCharsets.UTF_8));
  }
}
