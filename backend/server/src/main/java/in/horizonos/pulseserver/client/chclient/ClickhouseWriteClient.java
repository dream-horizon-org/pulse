package in.horizonos.pulseserver.client.chclient;

import static java.time.temporal.ChronoUnit.SECONDS;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ConnectionReuseStrategy;
import com.clickhouse.client.api.enums.Protocol;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.insert.InsertSettings;
import com.clickhouse.data.ClickHouseFormat;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import in.horizonos.pulseserver.config.ClickhouseConfig;
import in.horizonos.pulseserver.errorgrouping.model.StackTraceEvent;
import io.reactivex.rxjava3.core.Single;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;

public class ClickhouseWriteClient {
  private final Client client;
  private final ObjectMapper mapper = new ObjectMapper();

  public ClickhouseWriteClient(ClickhouseConfig config) {
    mapper.configOverride(String.class)
        .setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.AS_EMPTY));
    client = new Client.Builder()
        .addEndpoint(Protocol.HTTP, config.getHost(), config.getPort(), false)
        .setUsername(config.getUsername())
        .setPassword(config.getPassword())
        .enableConnectionPool(true)
        .setMaxConnections(32)
        .setKeepAliveTimeout(60, SECONDS)
        .setConnectionRequestTimeout(5, SECONDS)
        .setConnectionReuseStrategy(ConnectionReuseStrategy.LIFO)
        .setSocketKeepAlive(true)
        .compressClientRequest(true)
        .build();
  }


  @SneakyThrows
  public Single<InsertResponse> insert(List<StackTraceEvent> events) {
    CompletableFuture<InsertResponse> future = client.insert(
        "otel.stack_trace_events",
        (OutputStream out) -> {
          try (OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
               SequenceWriter seq = mapper.writer()
                   .withRootValueSeparator("\n") // NDJSON
                   .writeValues(w)) {
            for (StackTraceEvent e : events) {
              seq.write(e); // serialize object -> one JSON line
            }
            // seq/w auto-flush/close here
          }
        },
        ClickHouseFormat.JSONEachRow,
        new InsertSettings()
    );

    return Single.create(emitter ->
        future.whenComplete((resp, err) -> {
          if (err != null) {
            emitter.onError(err);
          } else {
            // Do NOT close here — return it to the caller.
            emitter.onSuccess(resp);
          }
        })
    );
  }


}
