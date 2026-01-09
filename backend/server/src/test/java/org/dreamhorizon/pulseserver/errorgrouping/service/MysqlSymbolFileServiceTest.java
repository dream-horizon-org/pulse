package org.dreamhorizon.pulseserver.errorgrouping.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Query;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MysqlSymbolFileServiceTest {

  @Mock
  private MysqlClient mysqlClient;

  @Mock
  private MySQLPool writerPool;

  @Mock
  private MySQLPool readerPool;

  @Mock
  private PreparedQuery<RowSet<Row>> preparedQuery;

  @Mock
  private Query<RowSet<Row>> query;

  @Mock
  private RowSet<Row> rowSet;

  @Mock
  private Row row;

  private MysqlSymbolFileService mysqlSymbolFileService;

  @BeforeEach
  void setUp() {
    lenient().when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    lenient().when(mysqlClient.getReaderPool()).thenReturn(readerPool);
    mysqlSymbolFileService = new MysqlSymbolFileService(mysqlClient);
  }

  private UploadMetadata createMetadata() {
    return UploadMetadata.builder()
        .appVersion("1.0.0")
        .versionCode("100")
        .platform("android")
        .type("JS")
        .bundleId("com.example.app")
        .build();
  }

  @Nested
  class ToBufferTests {
    @Test
    void shouldConvertInputStreamToBuffer() throws Exception {
      String content = "test content";
      InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

      Buffer result = MysqlSymbolFileService.toBuffer(inputStream);

      assertNotNull(result);
      assertEquals(content, result.toString(StandardCharsets.UTF_8));
    }

    @Test
    void shouldHandleEmptyInputStream() throws Exception {
      InputStream inputStream = new ByteArrayInputStream(new byte[0]);

      Buffer result = MysqlSymbolFileService.toBuffer(inputStream);

      assertNotNull(result);
      assertEquals(0, result.length());
    }
  }

  @Nested
  class UploadFileTests {
    @Test
    void shouldUploadFileSuccessfully() {
      UploadMetadata metadata = createMetadata();
      String content = "source map content";
      InputStream fileInputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

      when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.just(rowSet));

      Single<Boolean> result = mysqlSymbolFileService.uploadFile("test.map", fileInputStream, metadata);

      Boolean success = result.blockingGet();
      assertTrue(success);
    }

    @Test
    void shouldReturnFalseOnUploadError() {
      UploadMetadata metadata = createMetadata();
      String content = "source map content";
      InputStream fileInputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

      when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.error(new RuntimeException("Database error")));

      Single<Boolean> result = mysqlSymbolFileService.uploadFile("test.map", fileInputStream, metadata);

      Boolean success = result.blockingGet();
      assertFalse(success);
    }

    @Test
    void shouldUploadFileWithBundleId() {
      UploadMetadata metadata = createMetadata();
      String content = "source map content";
      InputStream fileInputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

      when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.just(rowSet));

      Single<Boolean> result = mysqlSymbolFileService.uploadFile("test.map", fileInputStream, metadata);

      Boolean success = result.blockingGet();
      assertTrue(success);
    }
  }

  @Nested
  class ReadFileTests {
    @Test
    void shouldReadFileSuccessfully() {
      UploadMetadata metadata = createMetadata();
      String content = "file content";
      Buffer buffer = Buffer.buffer(content.getBytes(StandardCharsets.UTF_8));

      RowIterator<Row> rowIterator = mock(RowIterator.class);
      when(rowIterator.hasNext()).thenReturn(true);
      when(rowIterator.next()).thenReturn(row);
      when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.just(rowSet));
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(row.getBuffer(0)).thenReturn(buffer);

      Single<Buffer> result = mysqlSymbolFileService.readFile(metadata);

      Buffer fileContent = result.blockingGet();
      assertNotNull(fileContent);
      assertEquals(content, fileContent.toString(StandardCharsets.UTF_8));
    }

    @Test
    void shouldThrowExceptionWhenFileNotFound() {
      UploadMetadata metadata = createMetadata();

      RowIterator<Row> rowIterator = mock(RowIterator.class);
      when(rowIterator.hasNext()).thenReturn(false);
      when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.just(rowSet));
      when(rowSet.iterator()).thenReturn(rowIterator);

      Single<Buffer> result = mysqlSymbolFileService.readFile(metadata);

      assertThrows(NoSuchElementException.class, () -> result.blockingGet());
    }
  }

  @Nested
  class ReadFileAsBytesTests {
    @Test
    void shouldReadFileAsBytes() {
      UploadMetadata metadata = createMetadata();
      byte[] content = "file content".getBytes(StandardCharsets.UTF_8);
      Buffer buffer = Buffer.buffer(content);

      RowIterator<Row> rowIterator = mock(RowIterator.class);
      when(rowIterator.hasNext()).thenReturn(true);
      when(rowIterator.next()).thenReturn(row);
      when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.just(rowSet));
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(row.getBuffer(0)).thenReturn(buffer);

      Single<byte[]> result = mysqlSymbolFileService.readFileAsBytes(metadata);

      byte[] fileContent = result.blockingGet();
      assertNotNull(fileContent);
      assertEquals(new String(content), new String(fileContent));
    }
  }

  @Nested
  class ReadFileAsStringTests {
    @Test
    void shouldReadFileAsString() {
      UploadMetadata metadata = createMetadata();
      String content = "file content";
      Buffer buffer = Buffer.buffer(content.getBytes(StandardCharsets.UTF_8));

      RowIterator<Row> rowIterator = mock(RowIterator.class);
      when(rowIterator.hasNext()).thenReturn(true);
      when(rowIterator.next()).thenReturn(row);
      when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.just(rowSet));
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(row.getBuffer(0)).thenReturn(buffer);

      Single<String> result = mysqlSymbolFileService.readFileAsString(metadata);

      String fileContent = result.blockingGet();
      assertNotNull(fileContent);
      assertEquals(content, fileContent);
    }

    @Test
    void shouldHandleUtf8Encoding() {
      UploadMetadata metadata = createMetadata();
      String content = "测试内容 🚀";
      Buffer buffer = Buffer.buffer(content.getBytes(StandardCharsets.UTF_8));

      RowIterator<Row> rowIterator = mock(RowIterator.class);
      when(rowIterator.hasNext()).thenReturn(true);
      when(rowIterator.next()).thenReturn(row);
      when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.just(rowSet));
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(row.getBuffer(0)).thenReturn(buffer);

      Single<String> result = mysqlSymbolFileService.readFileAsString(metadata);

      String fileContent = result.blockingGet();
      assertEquals(content, fileContent);
    }
  }
}

