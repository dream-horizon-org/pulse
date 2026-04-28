package org.dreamhorizon.pulseserver.client.chclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.DataStreamWriter;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.insert.InsertSettings;
import com.clickhouse.client.api.query.QueryResponse;
import com.clickhouse.data.ClickHouseFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ClickhouseWriteClient}. The underlying ClickHouse {@link Client} is swapped
 * with a Mockito mock via reflection so we don't have to contact a real ClickHouse server.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClickhouseWriteClientTest {

  private ClickhouseConfig config;
  private ObjectMapper objectMapper;
  private ClickhouseWriteClient writeClient;
  private Client mockUnderlyingClient;

  @BeforeEach
  void setUp() throws Exception {
    config = new ClickhouseConfig(
        "r2dbc:clickhouse://localhost/default",
        "default",
        "",
        1,
        4,
        "localhost",
        8123,
        "cluster");
    objectMapper = new ObjectMapper();

    writeClient = new ClickhouseWriteClient(config, objectMapper);

    mockUnderlyingClient = mock(Client.class);
    java.lang.reflect.Field clientField = ClickhouseWriteClient.class.getDeclaredField("client");
    clientField.setAccessible(true);
    clientField.set(writeClient, mockUnderlyingClient);
  }

  @Nested
  class Construction {

    @Test
    void shouldConstructWithoutErrors() {
      // Re-create in isolation — asserts the constructor path runs end-to-end.
      ClickhouseWriteClient localClient = new ClickhouseWriteClient(config, new ObjectMapper());
      assertThat(localClient).isNotNull();
    }
  }

  @Nested
  class ExecuteSql {

    @Test
    void shouldReturnTrueForNullSql() {
      Single<Boolean> result = writeClient.executeSql(null);
      result.test()
          .assertComplete()
          .assertValue(true);
    }

    @Test
    void shouldReturnTrueForBlankSql() {
      writeClient.executeSql("   ")
          .test()
          .assertComplete()
          .assertValue(true);
    }

    @Test
    void shouldReturnTrueOnSuccessfulQuery() {
      QueryResponse mockResponse = mock(QueryResponse.class);
      CompletableFuture<QueryResponse> future = CompletableFuture.completedFuture(mockResponse);
      when(mockUnderlyingClient.query(anyString())).thenReturn(future);

      TestObserver<Boolean> observer = writeClient.executeSql("SELECT 1").test();

      observer.awaitDone(5, TimeUnit.SECONDS)
          .assertComplete()
          .assertValue(true);
      verify(mockUnderlyingClient).query("SELECT 1");
    }

    @Test
    void shouldPropagateErrorFromQuery() {
      CompletableFuture<QueryResponse> future = new CompletableFuture<>();
      future.completeExceptionally(new RuntimeException("boom"));
      when(mockUnderlyingClient.query(anyString())).thenReturn(future);

      TestObserver<Boolean> observer = writeClient.executeSql("SELECT bad").test();

      observer.awaitDone(5, TimeUnit.SECONDS)
          .assertError(RuntimeException.class);
    }

  }

  @Nested
  class Insert {

    @Test
    void shouldReturnResponseOnSuccessfulInsert() {
      InsertResponse mockResponse = mock(InsertResponse.class);
      CompletableFuture<InsertResponse> future = CompletableFuture.completedFuture(mockResponse);
      when(mockUnderlyingClient.insert(
          anyString(),
          any(DataStreamWriter.class),
          any(ClickHouseFormat.class),
          any(InsertSettings.class)))
          .thenReturn(future);

      StackTraceEvent event = StackTraceEvent.builder()
          .timestamp("2024-01-01T00:00:00Z")
          .title("NPE")
          .exceptionType("NullPointerException")
          .build();

      TestObserver<InsertResponse> observer = writeClient.insert(List.of(event)).test();

      observer.awaitDone(5, TimeUnit.SECONDS)
          .assertComplete()
          .assertValue(mockResponse);

      verify(mockUnderlyingClient).insert(
          eq("otel.stack_trace_events"),
          any(DataStreamWriter.class),
          eq(ClickHouseFormat.JSONEachRow),
          any(InsertSettings.class));
    }

    @Test
    void shouldPropagateErrorOnInsertFailure() {
      CompletableFuture<InsertResponse> future = new CompletableFuture<>();
      future.completeExceptionally(new RuntimeException("insert failed"));
      when(mockUnderlyingClient.insert(
          anyString(),
          any(DataStreamWriter.class),
          any(ClickHouseFormat.class),
          any(InsertSettings.class)))
          .thenReturn(future);

      TestObserver<InsertResponse> observer =
          writeClient.insert(Collections.emptyList()).test();

      observer.awaitDone(5, TimeUnit.SECONDS)
          .assertError(RuntimeException.class);
    }

    @Test
    void shouldInvokeWriterLambdaToSerializeEvents() throws Exception {
      // Capture the DataStreamWriter to invoke it against a dummy OutputStream. This exercises the
      // NDJSON serialization path (lines inside the lambda).
      InsertResponse mockResponse = mock(InsertResponse.class);
      CompletableFuture<InsertResponse> future = CompletableFuture.completedFuture(mockResponse);

      org.mockito.ArgumentCaptor<DataStreamWriter> captor =
          org.mockito.ArgumentCaptor.forClass(DataStreamWriter.class);

      when(mockUnderlyingClient.insert(
          anyString(),
          captor.capture(),
          any(ClickHouseFormat.class),
          any(InsertSettings.class)))
          .thenReturn(future);

      List<StackTraceEvent> events = List.of(
          StackTraceEvent.builder().title("A").build(),
          StackTraceEvent.builder().title("B").build());

      writeClient.insert(events).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      captor.getValue().onOutput(out);

      String ndjson = out.toString(java.nio.charset.StandardCharsets.UTF_8);
      assertThat(ndjson).contains("\"Title\":\"A\"");
      assertThat(ndjson).contains("\"Title\":\"B\"");
    }
  }
}
