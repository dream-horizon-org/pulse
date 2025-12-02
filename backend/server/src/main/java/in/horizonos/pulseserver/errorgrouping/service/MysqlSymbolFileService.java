package in.horizonos.pulseserver.errorgrouping.service;

import com.google.inject.Inject;
import in.horizonos.pulseserver.client.mysql.MysqlClient;
import in.horizonos.pulseserver.errorgrouping.model.UploadMetadata;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Slf4j
public class MysqlSymbolFileService extends SymbolFileService {
  private final MysqlClient d11MysqlClient;

  @SneakyThrows
  public static Buffer toBuffer(InputStream in) {
    return Buffer.buffer(in.readAllBytes());
  }

  @Override
  public Single<Boolean> uploadFile(String fileName, InputStream fileInputStream, UploadMetadata metadata) {
    final String sql =
        "INSERT INTO symbol_files " +
            "  (app_version, app_version_code, platform, framework, file_content) " +
            "VALUES (?,?,?,?,?) " +
            "ON DUPLICATE KEY UPDATE file_content = VALUES(file_content)";

    return d11MysqlClient.getWriterPool()
        .preparedQuery(sql)
        .execute(Tuple.wrap(Arrays.asList(metadata.getAppVersion(),
            metadata.getVersionCode(),
            metadata.getPlatform(),
            metadata.getType(),
            toBuffer(fileInputStream))))
        .map(rows -> true)
        .onErrorResumeNext(err -> {
          log.error("Exception while uploading mapping file for {}: {}", metadata.toString(), err.getMessage());
          return Single.just(false);
        });
  }

  public Single<Buffer> readFile(UploadMetadata metadata) {
    log.info("Fetching symbol file from DATABASE for: {}", metadata);

    final String sql = """
        SELECT file_content
        FROM symbol_files
        WHERE app_version=? AND app_version_code=? AND platform=? AND framework=?
        LIMIT 1
        """;

    Tuple params = Tuple.of(
        metadata.getAppVersion(),
        metadata.getVersionCode(),
        metadata.getPlatform(),
        metadata.getType()
    );

    return d11MysqlClient.getReaderPool()
        .preparedQuery(sql)
        .execute(params)
        .map((RowSet<Row> rows) -> {
          var it = rows.iterator();
          if (!it.hasNext()) {
            log.warn("No symbol file found in database for: {}", metadata);
            throw new NoSuchElementException("No symbol file found for: " + metadata);
          }
          Row row = it.next();
          log.info("Successfully fetched symbol file from DATABASE for: {}", metadata);
          return row.getBuffer(0);
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
