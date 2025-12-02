package org.dreamhorizon.pulseserver.client.chclient;

import com.clickhouse.client.api.insert.InsertResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.service.IAnalyticalStoreClient;

@Slf4j
@Data
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ClickhouseQueryService implements IAnalyticalStoreClient<GetRawUserEventsResponseDto> {
  private final ClickhouseReadClient clickhouseReadClient;
  private final ClickhouseWriteClient clickhouseWriteClient;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());


  @Override
  public Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> executeQueryOrCreateJob(QueryConfiguration queryConfig) {
    final List<GetRawUserEventsResponseDto.Field> schemaFields = new ArrayList<>();

    return Single.fromPublisher(clickhouseReadClient.getPool().create())
        .flatMap(conn -> {
          return Flowable.fromPublisher(conn.createStatement(queryConfig.getQuery()).execute())
              .flatMap(result -> {
                return result.map((row, md) -> {
                  if (schemaFields.isEmpty()) {
                    for (int i = 0; i < md.getColumnMetadatas().size(); i++) {
                      schemaFields.add(new GetRawUserEventsResponseDto.Field(
                          md.getColumnMetadatas().get(i).getName()
                      ));
                    }
                  }
                  List<GetRawUserEventsResponseDto.RowField> rowFields = new ArrayList<>();
                  for (int i = 0; i < md.getColumnMetadatas().size(); i++) {
                    rowFields.add(new GetRawUserEventsResponseDto.RowField(row.get(i)));
                  }
                  return new GetRawUserEventsResponseDto.Row(rowFields);
                });
              })
              .toList()
              .map(rows -> {
                GetRawUserEventsResponseDto.Schema schema = new GetRawUserEventsResponseDto.Schema(schemaFields);
                GetRawUserEventsResponseDto responseData = GetRawUserEventsResponseDto.builder()
                    .schema(schema)
                    .rows(rows)
                    .totalRows((long) rows.size())
                    .build();
                return GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
                    .data(responseData)
                    .jobComplete(true)
                    .build();
              })
              .doFinally(() -> Completable.fromPublisher(conn.close()).subscribe());
        })
        .onErrorResumeNext(err -> {
          return Single.error(new Exception("Failed to execute query", err));
        });
  }

  @Override
  public <T> Single<QueryResultResponse<T>> executeQueryOrCreateJob(QueryConfiguration queryConfig, Class<T> clazz) {

    return Single.fromPublisher(clickhouseReadClient.getPool().create())
        .flatMap(conn ->
            Flowable.fromPublisher(conn.createStatement(queryConfig.getQuery()).execute())
                .flatMap(result -> result.map((row, md) -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  for (int i = 0; i < md.getColumnMetadatas().size(); i++) {
                    m.put(md.getColumnMetadatas().get(i).getName(), row.get(i).toString());
                  }
                  return m;
                }))
                .toList()
                .flatMap(maps -> {
                  List<T> mappedRows = new ArrayList<>(maps.size());
                  for (Map<String, Object> map : maps) {
                    mappedRows.add(objectMapper.convertValue(map, clazz));
                  }
                  return Single.just(
                      QueryResultResponse.<T>builder()
                          .jobComplete(true)
                          .rows(mappedRows)
                          .build()
                  );
                })
                .doFinally(() -> Completable.fromPublisher(conn.close()).subscribe())
        )
        .onErrorResumeNext(err -> {
          return Single.error(new Exception("Failed to execute query", err));
        });
  }

  public Single<Long> insertStackTraces(List<StackTraceEvent> events) {
    return clickhouseWriteClient.insert(events)
        .map(InsertResponse::getWrittenRows);
  }
}
