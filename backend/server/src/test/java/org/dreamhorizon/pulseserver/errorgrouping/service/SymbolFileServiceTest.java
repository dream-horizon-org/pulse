package org.dreamhorizon.pulseserver.errorgrouping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

  MysqlSymbolFileService symbolFileService;

  @BeforeEach
  void setUp() {
    symbolFileService = new MysqlSymbolFileService(mysqlClient);
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
    when(writerPool.preparedQuery(any(String.class))).thenReturn(writerPreparedQuery);
    when(readerPool.preparedQuery(any(String.class))).thenReturn(readerPreparedQuery);
  }

  @Nested
  class UploadFilesFromSymbolFileService {

    @Test
    void shouldReturnFalseWhenFilePartsNull() {
      ConcreteSymbolFileService service = new ConcreteSymbolFileService();

      Boolean result = service.uploadFiles("proj-1", null, List.of()).blockingGet();

      assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenFilePartsEmpty() {
      ConcreteSymbolFileService service = new ConcreteSymbolFileService();

      Boolean result = service.uploadFiles("proj-1", List.of(), List.of()).blockingGet();

      assertThat(result).isFalse();
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
      verify(mysqlClient).getWriterPool();
    }

    @Test
    void shouldReturnFalseWhenUploadFails() {
      when(writerPreparedQuery.execute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("DB error")));

      UploadMetadata metadata = UploadMetadata.builder()
          .projectId("proj-1")
          .appVersion("1.0")
          .versionCode("1")
          .platform("android")
          .type("framework")
          .bundleId("com.test")
          .build();
      InputStream input = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));

      Boolean result = symbolFileService.uploadFile("file.txt", input, metadata).blockingGet();

      assertThat(result).isFalse();
    }
  }

  @Nested
  class MysqlSymbolFileServiceRead {

    @Test
    void shouldReadFileAsString() {
      Row row = org.mockito.Mockito.mock(Row.class);
      when(row.getBuffer(0)).thenReturn(Buffer.buffer("file content"));
      RowSet<Row> rowSet = org.mockito.Mockito.mock(RowSet.class);
      RowIterator<Row> iterator = org.mockito.Mockito.mock(RowIterator.class);
      when(rowSet.iterator()).thenReturn(iterator);
      when(iterator.hasNext()).thenReturn(true).thenReturn(false);
      when(iterator.next()).thenReturn(row);

      when(readerPreparedQuery.execute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet));

      UploadMetadata metadata = UploadMetadata.builder()
          .projectId("proj-1")
          .appVersion("1.0")
          .versionCode("1")
          .platform("android")
          .type("framework")
          .build();

      String result = symbolFileService.readFileAsString(metadata).blockingGet();

      assertThat(result).isEqualTo("file content");
      verify(mysqlClient).getReaderPool();
    }

    @Test
    void shouldReadFileAsBytes() {
      byte[] bytes = "binary content".getBytes(StandardCharsets.UTF_8);
      Row row = org.mockito.Mockito.mock(Row.class);
      when(row.getBuffer(0)).thenReturn(Buffer.buffer(bytes));
      RowSet<Row> rowSet = org.mockito.Mockito.mock(RowSet.class);
      RowIterator<Row> iterator = org.mockito.Mockito.mock(RowIterator.class);
      when(rowSet.iterator()).thenReturn(iterator);
      when(iterator.hasNext()).thenReturn(true).thenReturn(false);
      when(iterator.next()).thenReturn(row);

      when(readerPreparedQuery.execute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet));

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

  @Nested
  class ToBuffer {

    @Test
    void shouldConvertInputStreamToBuffer() {
      byte[] data = "test data".getBytes(StandardCharsets.UTF_8);
      InputStream input = new ByteArrayInputStream(data);

      Buffer buffer = MysqlSymbolFileService.toBuffer(input);

      assertThat(buffer.getBytes()).isEqualTo(data);
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
