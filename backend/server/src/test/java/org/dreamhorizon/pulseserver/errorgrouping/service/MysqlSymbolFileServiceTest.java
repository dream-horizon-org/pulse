package org.dreamhorizon.pulseserver.errorgrouping.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

  @Mock
  private S3SymbolFileService s3SymbolFileService;

  private MysqlSymbolFileService mysqlSymbolFileService;

  @BeforeEach
  void setUp() {
    lenient().when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    lenient().when(mysqlClient.getReaderPool()).thenReturn(readerPool);
    mysqlSymbolFileService = new MysqlSymbolFileService(mysqlClient, s3SymbolFileService);
  }

  private UploadMetadata createMetadata() {
    return UploadMetadata.builder()
        .appVersion("1.0.0")
        .versionCode("100")
        .platform("android")
        .type("JS")
        .bundleId("com.example.app")
        .projectId("test-project")
        .build();
  }

  @Nested
  class UploadFileTests {
    @Test
    void shouldUploadFileSuccessfully() {
      UploadMetadata metadata = createMetadata();
      String content = "source map content";
      InputStream fileInputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

      when(s3SymbolFileService.uploadFile(any(), any())).thenReturn(Single.just("symbols/android/test-project/1.0.0/100/JS/test.map"));
      when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.just(rowSet));

      Single<Boolean> result = mysqlSymbolFileService.uploadFile("test.map", fileInputStream, metadata);

      Boolean success = result.blockingGet();
      assertTrue(success);
    }

    @Test
    void shouldPropagateErrorOnS3Failure() {
      UploadMetadata metadata = createMetadata();
      String content = "source map content";
      InputStream fileInputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

      when(s3SymbolFileService.uploadFile(any(), any())).thenReturn(Single.error(new RuntimeException("S3 error")));

      Single<Boolean> result = mysqlSymbolFileService.uploadFile("test.map", fileInputStream, metadata);

      assertThrows(RuntimeException.class, () -> result.blockingGet());
    }

    @Test
    void shouldPropagateErrorOnDbFailure() {
      UploadMetadata metadata = createMetadata();
      String content = "source map content";
      InputStream fileInputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

      when(s3SymbolFileService.uploadFile(any(), any())).thenReturn(Single.just("symbols/test-key"));
      when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.error(new RuntimeException("Database error")));

      Single<Boolean> result = mysqlSymbolFileService.uploadFile("test.map", fileInputStream, metadata);

      assertThrows(RuntimeException.class, () -> result.blockingGet());
    }
  }

  @Nested
  class ReadFileTests {
    @Test
    void shouldReadFileFromS3() {
      UploadMetadata metadata = createMetadata();
      String content = "file content";
      String s3Key = "symbols/android/test-project/1.0.0/100/JS/test.map";

      RowIterator<Row> rowIterator = mock(RowIterator.class);
      when(rowIterator.hasNext()).thenReturn(true);
      when(rowIterator.next()).thenReturn(row);
      when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.just(rowSet));
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(row.getString(0)).thenReturn(s3Key);
      when(s3SymbolFileService.downloadFileAsBytes(s3Key))
          .thenReturn(Single.just(content.getBytes(StandardCharsets.UTF_8)));

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

    @Test
    void shouldThrowExceptionWhenS3KeyIsNull() {
      UploadMetadata metadata = createMetadata();

      RowIterator<Row> rowIterator = mock(RowIterator.class);
      when(rowIterator.hasNext()).thenReturn(true);
      when(rowIterator.next()).thenReturn(row);
      when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.just(rowSet));
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(row.getString(0)).thenReturn(null);

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
      String s3Key = "symbols/test-key";

      RowIterator<Row> rowIterator = mock(RowIterator.class);
      when(rowIterator.hasNext()).thenReturn(true);
      when(rowIterator.next()).thenReturn(row);
      when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.just(rowSet));
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(row.getString(0)).thenReturn(s3Key);
      when(s3SymbolFileService.downloadFileAsBytes(s3Key)).thenReturn(Single.just(content));

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
      String s3Key = "symbols/test-key";

      RowIterator<Row> rowIterator = mock(RowIterator.class);
      when(rowIterator.hasNext()).thenReturn(true);
      when(rowIterator.next()).thenReturn(row);
      when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.just(rowSet));
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(row.getString(0)).thenReturn(s3Key);
      when(s3SymbolFileService.downloadFileAsBytes(s3Key))
          .thenReturn(Single.just(content.getBytes(StandardCharsets.UTF_8)));

      Single<String> result = mysqlSymbolFileService.readFileAsString(metadata);

      String fileContent = result.blockingGet();
      assertNotNull(fileContent);
      assertEquals(content, fileContent);
    }

    @Test
    void shouldHandleUtf8Encoding() {
      UploadMetadata metadata = createMetadata();
      String content = "测试内容 🚀";
      String s3Key = "symbols/test-key";

      RowIterator<Row> rowIterator = mock(RowIterator.class);
      when(rowIterator.hasNext()).thenReturn(true);
      when(rowIterator.next()).thenReturn(row);
      when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.execute(any())).thenReturn(Single.just(rowSet));
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(row.getString(0)).thenReturn(s3Key);
      when(s3SymbolFileService.downloadFileAsBytes(s3Key))
          .thenReturn(Single.just(content.getBytes(StandardCharsets.UTF_8)));

      Single<String> result = mysqlSymbolFileService.readFileAsString(metadata);

      String fileContent = result.blockingGet();
      assertEquals(content, fileContent);
    }
  }
}
