package org.dreamhorizon.pulseserver.errorgrouping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SymbolFileServiceTest {

  @Mock
  MysqlClient mysqlClient;

  @Mock
  MySQLPool writerPool;

  @Mock
  MySQLPool readerPool;

  @Mock
  PreparedQuery<RowSet<Row>> writerPreparedQuery;

  @Mock
  PreparedQuery<RowSet<Row>> readerPreparedQuery;

  @Mock
  S3SymbolFileService s3SymbolFileService;

  MysqlSymbolFileService symbolFileService;

  @BeforeEach
  void setUp() {
    symbolFileService = new MysqlSymbolFileService(mysqlClient, s3SymbolFileService);
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
    when(writerPool.preparedQuery(any(String.class))).thenReturn(writerPreparedQuery);
    when(readerPool.preparedQuery(any(String.class))).thenReturn(readerPreparedQuery);
  }

  @Nested
  class UploadFilesFromSymbolFileService {

    @Test
    void shouldThrowErrorWhenFilePartsNull() {
      ConcreteSymbolFileService service = new ConcreteSymbolFileService();

      assertThrows(IllegalArgumentException.class,
          () -> service.uploadFiles("proj-1", null, List.of()).blockingGet());
    }

    @Test
    void shouldThrowErrorWhenFilePartsEmpty() {
      ConcreteSymbolFileService service = new ConcreteSymbolFileService();

      assertThrows(IllegalArgumentException.class,
          () -> service.uploadFiles("proj-1", List.of(), List.of()).blockingGet());
    }

    @Test
    void shouldSetProjectIdOnMetadata() throws Exception {
      ConcreteSymbolFileService service = new ConcreteSymbolFileService();
      UploadMetadata meta = UploadMetadata.builder()
          .fileName("mapping.txt")
          .type("framework")
          .appVersion("1.0")
          .versionCode("1")
          .platform("android")
          .bundleId("com.test")
          .build();
      InputPart part = createInputPart("mapping.txt", "content");

      service.uploadFiles("proj-123", List.of(part), List.of(meta)).blockingGet();

      assertThat(meta.getProjectId()).isEqualTo("proj-123");
    }

    private InputPart createInputPart(String fileName, String content) throws java.io.IOException {
      InputPart part = org.mockito.Mockito.mock(InputPart.class);
      MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
      headers.add("Content-Disposition", "form-data; name=\"fileContent\"; filename=\"" + fileName + "\"");
      when(part.getHeaders()).thenReturn(headers);
      when(part.getBody(InputStream.class, null))
          .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
      return part;
    }
  }

  @Nested
  class MysqlSymbolFileServiceUpload {

    @Test
    void shouldUploadFileSuccessfully() {
      RowSet<Row> rowSet = org.mockito.Mockito.mock(RowSet.class);
      when(s3SymbolFileService.uploadFile(any(), any())).thenReturn(Single.just("symbols/test-key"));
      when(writerPreparedQuery.execute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      UploadMetadata metadata = UploadMetadata.builder()
          .projectId("proj-1")
          .appVersion("1.0")
          .versionCode("1")
          .platform("android")
          .type("framework")
          .bundleId("com.test")
          .build();
      InputStream input = new ByteArrayInputStream("symbol data".getBytes(StandardCharsets.UTF_8));

      Boolean result = symbolFileService.uploadFile("mapping.txt", input, metadata).blockingGet();

      assertThat(result).isTrue();
    }

    @Test
    void shouldPropagateErrorWhenUploadFails() {
      when(s3SymbolFileService.uploadFile(any(), any()))
          .thenReturn(Single.error(new RuntimeException("S3 error")));

      UploadMetadata metadata = UploadMetadata.builder()
          .projectId("proj-1")
          .appVersion("1.0")
          .versionCode("1")
          .platform("android")
          .type("framework")
          .bundleId("com.test")
          .build();
      InputStream input = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));

      assertThrows(RuntimeException.class,
          () -> symbolFileService.uploadFile("file.txt", input, metadata).blockingGet());
    }
  }

  @Nested
  class MysqlSymbolFileServiceRead {

    @Test
    void shouldReadFileAsString() {
      String content = "file content";
      String s3Key = "symbols/test-key";

      Row row = org.mockito.Mockito.mock(Row.class);
      when(row.getString(0)).thenReturn(s3Key);
      RowSet<Row> rowSet = org.mockito.Mockito.mock(RowSet.class);
      RowIterator<Row> iterator = org.mockito.Mockito.mock(RowIterator.class);
      when(rowSet.iterator()).thenReturn(iterator);
      when(iterator.hasNext()).thenReturn(true).thenReturn(false);
      when(iterator.next()).thenReturn(row);
      when(readerPreparedQuery.execute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(s3SymbolFileService.downloadFileAsBytes(s3Key))
          .thenReturn(Single.just(content.getBytes(StandardCharsets.UTF_8)));

      UploadMetadata metadata = UploadMetadata.builder()
          .projectId("proj-1")
          .appVersion("1.0")
          .versionCode("1")
          .platform("android")
          .type("framework")
          .build();

      String result = symbolFileService.readFileAsString(metadata).blockingGet();

      assertThat(result).isEqualTo(content);
    }

    @Test
    void shouldReadFileAsBytes() {
      byte[] bytes = "binary content".getBytes(StandardCharsets.UTF_8);
      String s3Key = "symbols/test-key";

      Row row = org.mockito.Mockito.mock(Row.class);
      when(row.getString(0)).thenReturn(s3Key);
      RowSet<Row> rowSet = org.mockito.Mockito.mock(RowSet.class);
      RowIterator<Row> iterator = org.mockito.Mockito.mock(RowIterator.class);
      when(rowSet.iterator()).thenReturn(iterator);
      when(iterator.hasNext()).thenReturn(true).thenReturn(false);
      when(iterator.next()).thenReturn(row);
      when(readerPreparedQuery.execute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(s3SymbolFileService.downloadFileAsBytes(s3Key)).thenReturn(Single.just(bytes));

      UploadMetadata metadata = UploadMetadata.builder()
          .projectId("proj-1")
          .appVersion("1.0")
          .versionCode("1")
          .platform("android")
          .type("framework")
          .build();

      byte[] result = symbolFileService.readFileAsBytes(metadata).blockingGet();

      assertThat(result).isEqualTo(bytes);
    }
  }

  /**
   * Concrete implementation for testing SymbolFileService.uploadFiles logic.
   */
  private static class ConcreteSymbolFileService extends SymbolFileService {
    @Override
    public Single<Boolean> uploadFile(String fileName, InputStream fileInputStream, UploadMetadata metadata) {
      return Single.just(true);
    }

    @Override
    public Single<String> readFileAsString(UploadMetadata uploadMetadata) {
      return Single.just("");
    }

    @Override
    public Single<byte[]> readFileAsBytes(UploadMetadata uploadMetadata) {
      return Single.just(new byte[0]);
    }
  }
}
