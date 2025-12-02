package in.horizonos.pulseserver.dao.interaction;

import static in.horizonos.pulseserver.dao.interaction.Queries.GET_ALL_ACTIVE_AND_RUNNING_INTERACTIONS;
import static in.horizonos.pulseserver.dao.interaction.Queries.GET_INTERACTIONS;
import static in.horizonos.pulseserver.dao.interaction.Queries.GET_INTERACTION_DETAILS;
import static in.horizonos.pulseserver.dao.interaction.Queries.GET_INTERACTION_FILTER_OPTIONS;
import static in.horizonos.pulseserver.dao.interaction.Queries.GET_TELEMETRY_FILTER_VALUES;
import static in.horizonos.pulseserver.dao.interaction.Queries.INSERT_INTERACTION;
import static in.horizonos.pulseserver.dao.interaction.Queries.UPDATE_INTERACTION;

import com.google.inject.Inject;
import in.horizonos.pulseserver.client.chclient.ClickhouseQueryService;
import in.horizonos.pulseserver.client.mysql.MysqlClient;
import in.horizonos.pulseserver.dao.interaction.models.InteractionDetailRow;
import in.horizonos.pulseserver.dto.response.GetRawUserEventsResponseDto;
import in.horizonos.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import in.horizonos.pulseserver.model.QueryConfiguration;
import in.horizonos.pulseserver.resources.interaction.models.InteractionFilterOptionsResponse;
import in.horizonos.pulseserver.resources.interaction.models.TelemetryFilterOptionsResponse;
import in.horizonos.pulseserver.service.interaction.models.CreateInteractionDaoResponse;
import in.horizonos.pulseserver.service.interaction.models.DeleteInteractionRequest;
import in.horizonos.pulseserver.service.interaction.models.GetInteractionsRequest;
import in.horizonos.pulseserver.service.interaction.models.GetInteractionsResponse;
import in.horizonos.pulseserver.service.interaction.models.InteractionDetailUploadMetadata;
import in.horizonos.pulseserver.service.interaction.models.InteractionDetails;
import in.horizonos.pulseserver.service.interaction.models.InteractionStatus;
import in.horizonos.pulseserver.util.ObjectMapperUtil;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InteractionDao {

  private static final DaoInteractionMapper mapper = DaoInteractionMapper.INSTANCE;
  private final MysqlClient d11MysqlClient;
  private final ClickhouseQueryService clickhouseQueryService;
  private final ObjectMapperUtil objectMapper;
  private final BaseInteractionDao baseInteractionDao;

  private static InteractionDetailUploadMetadata buildUploadMetadata(Long interactionId) {
    return InteractionDetailUploadMetadata
        .builder()
        .interactionId(interactionId)
        .status(InteractionDetailUploadMetadata.Status.PENDING)
        .build();
  }

  private static Single<Long> getLastInsertedId(RowSet<Row> rowSet) {
    if (rowSet.rowCount() == 0) {
      return Single.error(new RuntimeException("Failed to insert interaction"));
    }

    return Single.just(Long.parseLong(rowSet.property(MySQLClient.LAST_INSERTED_ID).toString()));
  }

  public Single<CreateInteractionDaoResponse> createInteractionAndUploadMetadata(InteractionDetails interaction) {
    InteractionDetailRow interactionDetailRow = mapper.toInteractionDetailRow(interaction);
    String interactionDetailRowStr = objectMapper.writeValueAsString(interactionDetailRow);

    Tuple tuple = Tuple.tuple()
        .addString(interaction.getName())
        .addString(interaction.getStatus().toString())
        .addString(interactionDetailRowStr)
        .addBoolean(false)
        .addLocalDateTime(interaction.getCreatedAt().toLocalDateTime())
        .addString(interaction.getCreatedBy())
        .addLocalDateTime(interaction.getUpdatedAt().toLocalDateTime())
        .addString(interaction.getUpdatedBy());

    return d11MysqlClient
        .getWriterPool()
        .rxGetConnection()
        .flatMap(conn -> conn.preparedQuery(INSERT_INTERACTION)
            .rxExecute(tuple)
            .flatMap(InteractionDao::getLastInsertedId)
            .map(interactionId -> {
              InteractionDetails insertedInteraction = interaction.toBuilder().id(interactionId).build();
              return CreateInteractionDaoResponse
                  .builder()
                  .interactionDetails(insertedInteraction)
                  .build();
            })
            .doFinally(conn::close));
  }


  public Single<InteractionDetailUploadMetadata> updateInteractionAndCreateUploadMetadata(InteractionDetails interaction) {
    InteractionDetailRow interactionDetailRow = mapper.toInteractionDetailRow(interaction);
    String interactionDetailRowStr = objectMapper.writeValueAsString(interactionDetailRow);

    Tuple tuple = Tuple.tuple()
        .addString(interaction.getStatus().toString())
        .addString(interactionDetailRowStr)
        .addLocalDateTime(interaction.getUpdatedAt().toLocalDateTime())
        .addString(interaction.getUpdatedBy())
        .addLong(interaction.getId());

    return d11MysqlClient
        .getWriterPool()
        .rxGetConnection()
        .flatMap(conn -> conn.preparedQuery(UPDATE_INTERACTION)
            .rxExecute(tuple)
            .map(row -> buildUploadMetadata(interaction.getId()))
            .doFinally(conn::close));
  }

  public Single<Boolean> isInteractionPresent(String interactionName) {
    return baseInteractionDao.isInteractionPresent(interactionName);
  }

  public Single<InteractionDetails> getInteractionDetails(@NotNull String useCaseId) {
    return d11MysqlClient
        .getReaderPool()
        .preparedQuery(GET_INTERACTION_DETAILS)
        .rxExecute(Tuple.of(useCaseId))
        .flatMap(rowSet -> mapRowToInteractionDetails(useCaseId, rowSet));
  }

  private @NotNull Single<InteractionDetails> mapRowToInteractionDetails(String useCaseId, RowSet<Row> rowSet) {
    if (rowSet.size() == 0) {
      return Single.error(new RuntimeException("No interaction found for useCaseId: " + useCaseId));
    }

    Row row = rowSet.iterator().next();
    return Single.just(mapInteractionRowToInteractionDetails(row));
  }

  private @NotNull InteractionDetails mapInteractionRowToInteractionDetails(Row row) {
    InteractionDetailRow details = objectMapper.readValue(row.getValue("details").toString(), InteractionDetailRow.class);

    return InteractionDetails
        .builder()
        .id(row.getLong("interaction_id"))
        .name(row.getString("name"))
        .description(details.getDescription())
        .status(InteractionStatus.fromString(row.getString("status")))
        .uptimeLowerLimitInMs(details.getUptimeLowerLimitInMs())
        .uptimeMidLimitInMs(details.getUptimeMidLimitInMs())
        .uptimeUpperLimitInMs(details.getUptimeUpperLimitInMs())
        .thresholdInMs(details.getThresholdInMs())
        .events(details.getEvents())
        .globalBlacklistedEvents(details.getGlobalBlacklistedEvents())
        .createdBy(row.getString("created_by"))
        .createdAt(Timestamp.valueOf(row.getLocalDateTime("created_at")))
        .updatedBy(row.getString("updated_by"))
        .updatedAt(Timestamp.valueOf(row.getLocalDateTime("last_updated_at")))
        .build();
  }

  public Single<GetInteractionsResponse> getInteractions(@Valid GetInteractionsRequest request) {
    return d11MysqlClient.getReaderPool()
        .preparedQuery(buildPaginatedGetInteractionsQuery(request))
        .rxExecute()
        .flatMap(rows -> {
          List<InteractionDetails> interactionDetails = new ArrayList<>();

          rows.forEach(row -> interactionDetails.add(mapInteractionRowToInteractionDetails(row)));

          Integer totalInteractions = 0;
          if (rows.iterator().hasNext()) {
            totalInteractions = rows.iterator().next().getInteger("total_interactions");
          }

          GetInteractionsResponse response = GetInteractionsResponse
              .builder()
              .interactions(interactionDetails)
              .totalInteractions(totalInteractions)
              .build();

          return Single.just(response);
        }).doOnError(error -> log.error("error in querying jobs : ", error));
  }

  public Single<List<InteractionDetails>> getAllActiveAndRunningInteractions() {
    return d11MysqlClient.getReaderPool()
        .preparedQuery(GET_ALL_ACTIVE_AND_RUNNING_INTERACTIONS)
        .rxExecute()
        .flatMap(rows -> {
          List<InteractionDetails> interactionDetails = new ArrayList<>();
          rows.forEach(row -> interactionDetails.add(mapInteractionRowToInteractionDetails(row)));
          return Single.just(interactionDetails);
        }).doOnError(error -> log.error("error in querying jobs : ", error));
  }

  private String buildPaginatedGetInteractionsQuery(GetInteractionsRequest request) {
    String query = GET_INTERACTIONS;
    if (StringUtils.isNotBlank(request.getUserEmail())) {
      query += " AND created_by = '" + request.getUserEmail() + "'";
    }

    if (request.getStatus() != null && StringUtils.isNotBlank(request.getStatus().name())) {
      query += " AND status = '" + request.getStatus() + "'";
    }

    if (StringUtils.isNotBlank(request.getName())) {
      query += " AND name LIKE '%" + request.getName() + "%'";
    }

    query += " limit " + request.getSize() + " offset " + request.getPage() * request.getSize() + ";";
    return query;
  }

  public Single<InteractionDetailUploadMetadata> deleteInteractionAndCreateUploadMetadata(
      DeleteInteractionRequest deleteInteractionRequest
  ) {
    return getInteractionDetails(deleteInteractionRequest.getName())
        .flatMap(interaction -> d11MysqlClient
            .getWriterPool()
            .rxGetConnection()
            .flatMap(conn -> baseInteractionDao.archiveJob(conn, interaction.getName(), interaction.getCreatedBy())
                .map(res -> buildUploadMetadata(interaction.getId()))
                .doFinally(conn::close))
        );
  }

  public Single<InteractionFilterOptionsResponse> getInteractionFilterOptions() {
    return d11MysqlClient.getReaderPool()
        .preparedQuery(GET_INTERACTION_FILTER_OPTIONS)
        .rxExecute()
        .flatMap(rows -> {
          Set<String> statuses = new HashSet<>();
          Set<String> createdByUsers = new HashSet<>();

          rows.forEach(row -> {
            String status = row.getString("status");
            String createdBy = row.getString("created_by");

            if (status != null) {
              statuses.add(status);
            }
            if (createdBy != null) {
              createdByUsers.add(createdBy);
            }
          });

          InteractionFilterOptionsResponse response = InteractionFilterOptionsResponse
              .builder()
              .statuses(statuses.stream().sorted().collect(Collectors.toList()))
              .createdBy(createdByUsers.stream().sorted().collect(Collectors.toList()))
              .build();

          return Single.just(response);
        }).doOnError(error -> log.error("error in fetching interaction filter options: ", error));
  }

  public Single<TelemetryFilterOptionsResponse> getTelemetryFilterOptions() {
    QueryConfiguration configuration = QueryConfiguration.newQuery(GET_TELEMETRY_FILTER_VALUES).build();

    return clickhouseQueryService.executeQueryOrCreateJob(configuration)
        .flatMap(this::buildTelemetryFilterResponse)
        .doOnError(error -> log.error("error in fetching telemetry filter options: ", error));
  }

  private Single<TelemetryFilterOptionsResponse> buildTelemetryFilterResponse(
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> response) {

    if (!response.isJobComplete()) {
      return Single.error(new RuntimeException("Failed to fetch telemetry filter data"));
    }

    Map<Integer, String> keyNameForIndexMap = new HashMap<>();
    List<GetRawUserEventsResponseDto.Field> fields = response.data.getSchema().getFields();
    IntStream.range(0, fields.size()).forEach(i -> keyNameForIndexMap.put(i, fields.get(i).getName()));

    Map<String, List<String>> filterValues = new HashMap<>();
    filterValues.put("appVersionCodes", new ArrayList<>());
    filterValues.put("deviceModels", new ArrayList<>());
    filterValues.put("networkProviders", new ArrayList<>());
    filterValues.put("states", new ArrayList<>());
    filterValues.put("osVersions", new ArrayList<>());
    filterValues.put("platforms", new ArrayList<>());

    if (response.data.getRows() != null && !response.data.getRows().isEmpty()) {
      GetRawUserEventsResponseDto.Row firstRow = response.data.getRows().get(0);

      for (int i = 0; i < firstRow.getF().size(); i++) {
        String fieldName = keyNameForIndexMap.get(i);
        Object value = firstRow.getF().get(i).getV();

        if (Objects.nonNull(value)) {
          filterValues.put(fieldName, objectMapper.convertValue(value,
              objectMapper.constructCollectionType(List.class, String.class)));
        }
      }
    }

    return Single.just(objectMapper.convertValue(filterValues, TelemetryFilterOptionsResponse.class));
  }
}