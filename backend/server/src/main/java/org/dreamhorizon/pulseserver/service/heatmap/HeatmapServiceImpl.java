package org.dreamhorizon.pulseserver.service.heatmap;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.heatmap.HeatmapQueries;
import org.dreamhorizon.pulseserver.dao.interaction.InteractionDao;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.models.Features;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapAppVersionRowDto;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapClickHouseRowDto;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapDataRestResponse;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapEventNameRowDto;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapFrustrationRestDto;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapInteractionMetadataRestDto;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapLayersRestDto;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapMetadataRestDto;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapPointRestDto;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetails;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class HeatmapServiceImpl implements HeatmapService {

  private static final int TIMEOUT_MS = 60_000;

  private static final String DEFAULT_SCREENSHOT_PLATFORM = "Android";
  private static final String DEFAULT_SCREENSHOT_BREAKPOINT = "Mobile_Medium";

  private final ConfigService configService;
  private final ClickhouseQueryService clickhouseQueryService;
  private final InteractionDao interactionDao;

  @Override
  public Single<HeatmapDataRestResponse> getHeatmapData(
      String screenName,
      String from,
      String to,
      String appVersion,
      String platform,
      String breakpoint,
      String geographicalRegion) {

    String projectId = ProjectContext.requireProjectId();

    if (screenName == null || screenName.isBlank()) {
      return Single.error(
          ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException("screenName is required"));
    }
    if (from == null || to == null) {
      return Single.error(
          ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException("from and to are required"));
    }

    final Instant fromInstant;
    final Instant toInstant;
    try {
      fromInstant = Instant.parse(from.trim());
      toInstant = Instant.parse(to.trim());
    } catch (DateTimeParseException e) {
      return Single.error(
          ServiceError.INVALID_REQUEST_PARAM.getCustomException(
              "from and to must be ISO-8601 instants, e.g. 2026-03-26T00:00:00Z"));
    }

    return configService
        .getActiveSdkConfig(projectId)
        .flatMap(
            cfg -> {
              if (!isHeatmapFeatureEnabled(cfg)) {
                return Single.error(
                    ServiceError.FORBIDDEN.getCustomException(
                        "Heatmaps are disabled for this project"));
              }
              return queryHeatmapAndBuildResponse(
                  projectId,
                  screenName,
                  fromInstant,
                  toInstant,
                  appVersion,
                  platform,
                  breakpoint,
                  geographicalRegion);
            })
        .doOnError(e -> log.error("Heatmap query failed for project {}", projectId, e));
  }

  private static boolean isHeatmapFeatureEnabled(PulseConfig config) {
    if (config == null || config.getFeatures() == null) {
      return false;
    }
    return config.getFeatures().stream()
        .filter(f -> f.getFeatureName() == Features.heatmap)
        .findFirst()
        .map(
            f ->
                f.getSessionSampleRate() != null && f.getSessionSampleRate() > 0.0)
        .orElse(false);
  }

  private Single<HeatmapDataRestResponse> queryHeatmapAndBuildResponse(
      String projectId,
      String screenName,
      Instant fromInstant,
      Instant toInstant,
      String appVersion,
      String platform,
      String breakpoint,
      String geographicalRegion) {

    String dateFrom = fromInstant.atZone(ZoneOffset.UTC).toLocalDate().toString();
    String dateTo = toInstant.atZone(ZoneOffset.UTC).toLocalDate().toString();

    String heatmapWhere =
        buildHeatmapWhereClause(
            projectId,
            dateFrom,
            dateTo,
            screenName,
            appVersion,
            platform,
            breakpoint,
            geographicalRegion);

    String aggregateSql = String.format(HeatmapQueries.HEATMAP_AGGREGATE, heatmapWhere);

    QueryConfiguration heatmapConfig =
        QueryConfiguration.newQuery(aggregateSql)
            .timeoutMs(TIMEOUT_MS)
            .tenantId(projectId)
            .projectId(projectId)
            .build();

    Single<List<HeatmapClickHouseRowDto>> heatmapSingle =
        clickhouseQueryService
            .executeQueryOrCreateJob(heatmapConfig, HeatmapClickHouseRowDto.class)
            .map(r -> r.getRows() != null ? r.getRows() : Collections.emptyList());

    Single<List<HeatmapInteractionMetadataRestDto>> interactionsSingle =
        fetchDistinctEventNames(projectId, screenName, fromInstant, toInstant)
            .flatMap(
                eventNames ->
                    interactionDao
                        .getAllActiveAndRunningInteractions(projectId)
                        .map(
                            interactions ->
                                eligibleInteractionNamesForScreen(
                                    interactions, new HashSet<>(eventNames))))
            .flatMap(
                eligibleNames -> {
                  if (eligibleNames.isEmpty()) {
                    return Single.just(Collections.emptyList());
                  }
                  String where =
                      buildInteractionsApdexWhereClause(
                          projectId,
                          fromInstant,
                          toInstant,
                          eligibleNames,
                          appVersion,
                          platform,
                          geographicalRegion);
                  String interactionsSql =
                      String.format(HeatmapQueries.INTERACTIONS_APDEX_FOR_INTERACTION_NAMES, where);
                  QueryConfiguration interactionsConfig =
                      QueryConfiguration.newQuery(interactionsSql)
                          .timeoutMs(TIMEOUT_MS)
                          .tenantId(projectId)
                          .projectId(projectId)
                          .build();
                  return clickhouseQueryService
                      .executeQueryOrCreateJob(
                          interactionsConfig, HeatmapInteractionMetadataRestDto.class)
                      .map(
                          r -> r.getRows() != null ? r.getRows() : Collections.emptyList());
                });

    Single<Optional<String>> resolvedScreenshotAppVersionSingle =
        nonBlankOrNull(appVersion) != null
            ? Single.just(Optional.of(nonBlankOrNull(appVersion)))
            : fetchLatestAppVersionInSlice(
                projectId,
                dateFrom,
                dateTo,
                screenName,
                platform,
                breakpoint,
                geographicalRegion);

    return Single.zip(
        heatmapSingle,
        interactionsSingle,
        resolvedScreenshotAppVersionSingle,
        (heatmapRows, interactionRows, resolvedAppVersionOpt) -> {
          String screenshotPlatform =
              nonBlankOrNull(platform) != null
                  ? nonBlankOrNull(platform)
                  : DEFAULT_SCREENSHOT_PLATFORM;
          String screenshotBreakpoint =
              nonBlankOrNull(breakpoint) != null
                  ? nonBlankOrNull(breakpoint)
                  : DEFAULT_SCREENSHOT_BREAKPOINT;
          String screenshotAppVersion =
              nonBlankOrNull(appVersion) != null
                  ? nonBlankOrNull(appVersion)
                  : resolvedAppVersionOpt.orElse(null);
          List<String> screenshotUrls =
              resolveScreenshotUrlsForScreen(
                  projectId,
                  screenName,
                  dateFrom,
                  dateTo,
                  screenshotAppVersion,
                  screenshotPlatform,
                  screenshotBreakpoint);
          return toResponse(
              heatmapRows,
              interactionRows,
              screenName,
              fromInstant,
              toInstant,
              screenshotUrls);
        });
  }

  /**
   * Distinct {@code AppVersion} in the heatmap slice (request filters only; no AppVersion
   * predicate), then greatest {@code major.minor.patch} via {@link #maxMajorMinorPatchVersion}.
   */
  private Single<Optional<String>> fetchLatestAppVersionInSlice(
      String projectId,
      String dateFrom,
      String dateTo,
      String screenName,
      String platform,
      String breakpoint,
      String geographicalRegion) {

    String where =
        buildHeatmapWhereClause(
            projectId,
            dateFrom,
            dateTo,
            screenName,
            null,
            platform,
            breakpoint,
            geographicalRegion);
    String sql = String.format(HeatmapQueries.DISTINCT_APP_VERSIONS_IN_SLICE, where);
    QueryConfiguration config =
        QueryConfiguration.newQuery(sql)
            .timeoutMs(TIMEOUT_MS)
            .tenantId(projectId)
            .projectId(projectId)
            .build();
    return clickhouseQueryService
        .executeQueryOrCreateJob(config, HeatmapAppVersionRowDto.class)
        .map(
            r -> {
              if (r.getRows() == null || r.getRows().isEmpty()) {
                return Optional.<String>empty();
              }
              List<String> versions =
                  r.getRows().stream()
                      .map(HeatmapAppVersionRowDto::getAppVersion)
                      .collect(Collectors.toList());
              return maxMajorMinorPatchVersion(versions);
            });
  }

  /**
   * Greatest {@code major.minor.patch} by integer segment comparison; skips strings that do not
   * parse as three integer segments.
   */
  private static Optional<String> maxMajorMinorPatchVersion(List<String> rawVersions) {
    if (rawVersions == null || rawVersions.isEmpty()) {
      return Optional.empty();
    }
    List<String> list =
        rawVersions.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    return Optional.ofNullable(findLatestMajorMinorPatch(list));
  }

  private static String findLatestMajorMinorPatch(List<String> versions) {
    if (versions == null || versions.isEmpty()) {
      return null;
    }
    return versions.stream()
        .filter(
            v -> {
              try {
                parseVersionSegment(v, 0);
                parseVersionSegment(v, 1);
                parseVersionSegment(v, 2);
                return true;
              } catch (NumberFormatException e) {
                return false;
              }
            })
        .max(
            Comparator.comparingInt((String v) -> parseVersionSegment(v, 0))
                .thenComparingInt(v -> parseVersionSegment(v, 1))
                .thenComparingInt(v -> parseVersionSegment(v, 2)))
        .orElse(null);
  }

  private static int parseVersionSegment(String version, int index) {
    String v =
        version.startsWith("v") || version.startsWith("V") ? version.substring(1) : version;
    String[] parts = v.split("\\.");
    return index < parts.length ? Integer.parseInt(parts[index]) : 0;
  }

  /**
   * Interaction {@code name} values whose t0 event (first configured event) appears among distinct
   * span event names on {@code screenName} (via {@code Events.Attributes['screen.name']}).
   */
  private static List<String> eligibleInteractionNamesForScreen(
      List<InteractionDetails> interactions, Set<String> eventNamesOnScreen) {
    if (eventNamesOnScreen.isEmpty()) {
      return Collections.emptyList();
    }
    return interactions.stream()
        .filter(i -> i.getEvents() != null && !i.getEvents().isEmpty())
        .filter(i -> eventNamesOnScreen.contains(i.getEvents().get(0).getName()))
        .map(InteractionDetails::getName)
        .distinct()
        .collect(Collectors.toList());
  }

  private Single<List<String>> fetchDistinctEventNames(
      String projectId, String screenName, Instant fromInstant, Instant toInstant) {
    String sql =
        String.format(
            HeatmapQueries.DISTINCT_EVENT_NAMES_ON_SCREEN,
            chString(projectId),
            chString(screenName),
            chString(fromInstant.toString()),
            chString(toInstant.toString()));
    QueryConfiguration config =
        QueryConfiguration.newQuery(sql)
            .timeoutMs(TIMEOUT_MS)
            .tenantId(projectId)
            .projectId(projectId)
            .build();
    return clickhouseQueryService
        .executeQueryOrCreateJob(config, HeatmapEventNameRowDto.class)
        .map(
            r -> {
              if (r.getRows() == null) {
                return Collections.<String>emptyList();
              }
              return r.getRows().stream()
                  .map(HeatmapEventNameRowDto::getEventName)
                  .filter(n -> n != null && !n.isBlank())
                  .distinct()
                  .collect(Collectors.toList());
            });
  }

  private HeatmapDataRestResponse toResponse(
      List<HeatmapClickHouseRowDto> rows,
      List<HeatmapInteractionMetadataRestDto> interactionsMetadata,
      String screenName,
      Instant fromInstant,
      Instant toInstant,
      List<String> screenshotUrls) {

    long totalNormal =
        rows.stream()
            .mapToLong(r -> r.getWeightNormal() != null ? r.getWeightNormal() : 0L)
            .sum();

    List<HeatmapPointRestDto> glow = new ArrayList<>();
    List<HeatmapPointRestDto> rage = new ArrayList<>();
    List<HeatmapPointRestDto> dead = new ArrayList<>();

    for (HeatmapClickHouseRowDto row : rows) {
      if (row.getXBin() == null || row.getYBin() == null) {
        continue;
      }
      double x = row.getXBin();
      double y = row.getYBin();
      long wN = row.getWeightNormal() != null ? row.getWeightNormal() : 0L;
      long wR = row.getWeightRage() != null ? row.getWeightRage() : 0L;
      long wD = row.getWeightDead() != null ? row.getWeightDead() : 0L;

      if (wN > 0) {
        glow.add(HeatmapPointRestDto.builder().x(x).y(y).weight(wN).build());
      }
      if (wR > 0) {
        rage.add(HeatmapPointRestDto.builder().x(x).y(y).weight(wR).build());
      }
      if (wD > 0) {
        dead.add(HeatmapPointRestDto.builder().x(x).y(y).weight(wD).build());
      }
    }

    List<HeatmapInteractionMetadataRestDto> interactionList =
        interactionsMetadata.stream()
            .filter(
                m ->
                    m.getInteractionName() != null
                        && !m.getInteractionName().isBlank())
            .collect(Collectors.toList());

    HeatmapMetadataRestDto meta =
        HeatmapMetadataRestDto.builder()
            .screenName(screenName)
            .screenshotUrls(screenshotUrls)
            .totalEvents(totalNormal)
            .fromDate(fromInstant.toString())
            .toDate(toInstant.toString())
            .build();

    HeatmapLayersRestDto layers =
        HeatmapLayersRestDto.builder()
            .glowMap(glow)
            .frustrationMap(
                HeatmapFrustrationRestDto.builder().rageTaps(rage).deadTaps(dead).build())
            .build();

    return HeatmapDataRestResponse.builder()
        .metadata(meta)
        .layers(layers)
        .interactionsMetadata(interactionList)
        .build();
  }

  /**
   * TODO: Load screenshot URLs for the screen (e.g. from S3 metadata or a project store). {@code
   * appVersion}, {@code platform}, and {@code breakpoint} are already resolved (defaults applied in
   * {@link #queryHeatmapAndBuildResponse} when the API omitted them). Returns an empty list until
   * implemented.
   */
  @SuppressWarnings("unused")
  private static List<String> resolveScreenshotUrlsForScreen(
      String projectId,
      String screenName,
      String dateFrom,
      String dateTo,
      String appVersion,
      String platform,
      String breakpoint) {
    return Collections.emptyList();
  }

  private static String nonBlankOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static String buildHeatmapWhereClause(
      String projectId,
      String dateFrom,
      String dateTo,
      String screenName,
      String appVersion,
      String platform,
      String breakpoint,
      String geographicalRegion) {

    StringBuilder sb = new StringBuilder();
    sb.append("ProjectId = '").append(chString(projectId)).append('\'');
    sb.append(" AND Date >= toDate('").append(chString(dateFrom)).append("')");
    sb.append(" AND Date <= toDate('").append(chString(dateTo)).append("')");
    sb.append(" AND ScreenName = '").append(chString(screenName)).append('\'');

    if (appVersion != null && !appVersion.isBlank()) {
      sb.append(" AND AppVersion = '").append(chString(appVersion)).append('\'');
    }
    if (platform != null && !platform.isBlank()) {
      sb.append(" AND Platform = '").append(chString(platform)).append('\'');
    }
    if (breakpoint != null && !breakpoint.isBlank()) {
      sb.append(" AND Breakpoint = '").append(chString(breakpoint)).append('\'');
    }
    if (geographicalRegion != null && !geographicalRegion.isBlank()) {
      sb.append(" AND GeographicalRegion = '").append(chString(geographicalRegion)).append('\'');
    }
    return sb.toString();
  }

  /**
   * Filters {@code otel.otel_traces} interaction spans by eligible interaction names (from MySQL t0
   * ∩ distinct event names on screen). Uses full {@code Timestamp} range. Breakpoint is not applied
   * (not a column on traces).
   */
  private static String buildInteractionsApdexWhereClause(
      String projectId,
      Instant fromInstant,
      Instant toInstant,
      List<String> interactionNames,
      String appVersion,
      String platform,
      String geographicalRegion) {

    StringBuilder sb = new StringBuilder();
    sb.append("ProjectId = '").append(chString(projectId)).append('\'');
    sb.append(" AND PulseType = 'interaction'");
    sb.append(" AND SpanAttributes['pulse.interaction.name'] IN (");
    for (int i = 0; i < interactionNames.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append('\'').append(chString(interactionNames.get(i))).append('\'');
    }
    sb.append(')');
    sb.append(" AND Timestamp >= parseDateTime64BestEffort('")
        .append(chString(fromInstant.toString()))
        .append("', 9, 'UTC')");
    sb.append(" AND Timestamp <= parseDateTime64BestEffort('")
        .append(chString(toInstant.toString()))
        .append("', 9, 'UTC')");

    if (appVersion != null && !appVersion.isBlank()) {
      sb.append(" AND AppVersion = '").append(chString(appVersion)).append('\'');
    }
    if (platform != null && !platform.isBlank()) {
      sb.append(" AND Platform = '").append(chString(platform)).append('\'');
    }
    if (geographicalRegion != null && !geographicalRegion.isBlank()) {
      sb.append(" AND GeoState = '").append(chString(geographicalRegion)).append('\'');
    }
    return sb.toString();
  }

  private static String chString(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("'", "''");
  }
}
