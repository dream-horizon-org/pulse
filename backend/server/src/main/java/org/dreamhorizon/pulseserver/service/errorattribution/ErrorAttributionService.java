package org.dreamhorizon.pulseserver.service.errorattribution;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheDao;
import org.dreamhorizon.pulseserver.dao.rootcause.models.RootCauseCacheRow;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionResult.RiskRatioRow;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQuerySpec;
import org.dreamhorizon.pulseserver.util.NumberCoercionUtils;
import org.dreamhorizon.pulseserver.util.ObjectMapperUtil;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ErrorAttributionService {

  public static final String SPEC_VERSION = "1";
  public static final int RR_SCALE = 4;
  public static final String DISCLAIMER =
      "This diagnostic summarizes observational associations only; it does not establish causation.";

  private final ClickhouseQueryService clickhouseQueryService;
  private final RootCauseCacheDao rootCauseCacheDao;
  private final ObjectMapperUtil objectMapper;
  private final RootCauseConfig rootCauseConfig;

  public Single<ErrorAttributionResult> getErrorAttribution(
      String projectId, String interactionName, Instant start, Instant end, boolean refresh) {
    int lookbackDays = rootCauseConfig.getLookbackDays();
    LocalDate anchorDate =
        start.atZone(ZoneOffset.UTC).toLocalDate().plusDays(lookbackDays - 1L);

    if (!refresh) {
      return rootCauseCacheDao
          .findByKey(projectId, interactionName, anchorDate)
          .flatMap(opt -> {
            ErrorAttributionResult cached = tryReadThroughCache(opt, end);
            if (cached != null) {
              return Single.just(cached);
            }
            return computeAndCacheAsync(projectId, interactionName, start, end, anchorDate);
          });
    }
    return computeAndCacheAsync(projectId, interactionName, start, end, anchorDate);
  }

  private ErrorAttributionResult tryReadThroughCache(Optional<RootCauseCacheRow> opt, Instant requestEndExclusive) {
    if (opt.isEmpty()) {
      return null;
    }
    RootCauseCacheRow row = opt.get();
    if (row.getWindowEndUtc() == null || !windowEndMatches(row, requestEndExclusive)) {
      return null;
    }
    String json = row.getErrorAttributionJson();
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      ErrorAttributionResult cached = objectMapper.readValue(json, ErrorAttributionResult.class);
      if (!SPEC_VERSION.equals(cached.getDiagnosticSpecVersion())) {
        return null;
      }
      return cached;
    } catch (Exception e) {
      log.warn(
          "Invalid error_attribution_json in root_cause_cache for project={} interaction={} date={}: {}",
          row.getProjectId(),
          row.getInteractionName(),
          row.getDate(),
          e.getMessage());
      return null;
    }
  }

  private static boolean windowEndMatches(RootCauseCacheRow row, Instant requestEndExclusive) {
    Instant stored = row.getWindowEndUtc().atZone(ZoneOffset.UTC).toInstant();
    Instant a = stored.truncatedTo(ChronoUnit.MILLIS);
    Instant b = requestEndExclusive.truncatedTo(ChronoUnit.MILLIS);
    return a.equals(b);
  }

  private Single<ErrorAttributionResult> computeAndCacheAsync(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive,
      LocalDate anchorDate) {
    RootCauseQuerySpec spec =
        ErrorAttributionQueryBuilder.build(projectId, interactionName, startInclusive, endExclusive);
    return clickhouseQueryService
        .executeRootCauseQuery(projectId, spec.sql(), spec.bindNames(), spec.bindValues())
        .map(this::rowsToMaps)
        .map(this::buildResult)
        .doOnSuccess(body -> scheduleAttributionCacheWrite(projectId, interactionName, anchorDate, body));
  }

  private void scheduleAttributionCacheWrite(
      String projectId, String interactionName, LocalDate anchorDate, ErrorAttributionResult httpBody) {
    ErrorAttributionResult forJson =
        httpBody.toBuilder().cachedAt(Instant.now()).build();
    final String json;
    try {
      json = objectMapper.writeValueAsString(forJson);
    } catch (RuntimeException e) {
      log.warn(
          "Error attribution cache write skipped: failed to serialize JSON for project={} interaction={}",
          projectId,
          interactionName,
          e);
      return;
    }
    rootCauseCacheDao
        .findByKey(projectId, interactionName, anchorDate)
        .flatMapCompletable(
            opt -> {
              if (opt.isEmpty()) {
                return Completable.complete();
              }
              return rootCauseCacheDao.upsertPreservingRcaRow(
                  opt.get(), json, LocalDateTime.now(ZoneOffset.UTC));
            })
        .subscribe(
            () -> {},
            e ->
                log.warn(
                    "Error attribution cache write failed for project={} interaction={}: {}",
                    projectId,
                    interactionName,
                    e.getMessage(),
                    e));
  }

  private ErrorAttributionResult buildResult(List<Map<String, Object>> rows) {
    Map<String, Object> raw =
        rows == null || rows.isEmpty() ? Map.of() : lowerKeyMap(rows.get(0));
    long nU = NumberCoercionUtils.toLong(raw.get("n_u"));
    long nPoorU = NumberCoercionUtils.toLong(raw.get("n_poor_u"));

    List<RiskRatioRow> riskRows = new ArrayList<>();
    riskRows.add(
        buildRiskRow(
            "crash",
            NumberCoercionUtils.toLong(raw.get("n_treated_crash")),
            NumberCoercionUtils.toLong(raw.get("n_control_crash")),
            NumberCoercionUtils.toLong(raw.get("n_treated_low_crash")),
            NumberCoercionUtils.toLong(raw.get("n_control_low_crash"))));
    riskRows.add(
        buildRiskRow(
            "anr",
            NumberCoercionUtils.toLong(raw.get("n_treated_anr")),
            NumberCoercionUtils.toLong(raw.get("n_control_anr")),
            NumberCoercionUtils.toLong(raw.get("n_treated_low_anr")),
            NumberCoercionUtils.toLong(raw.get("n_control_low_anr"))));
    riskRows.add(
        buildRiskRow(
            "non_fatal",
            NumberCoercionUtils.toLong(raw.get("n_treated_nf")),
            NumberCoercionUtils.toLong(raw.get("n_control_nf")),
            NumberCoercionUtils.toLong(raw.get("n_treated_low_nf")),
            NumberCoercionUtils.toLong(raw.get("n_control_low_nf"))));
    riskRows.add(
        buildRiskRow(
            "api",
            NumberCoercionUtils.toLong(raw.get("n_treated_api")),
            NumberCoercionUtils.toLong(raw.get("n_control_api")),
            NumberCoercionUtils.toLong(raw.get("n_treated_low_api")),
            NumberCoercionUtils.toLong(raw.get("n_control_low_api"))));

    boolean insufficient = nPoorU < 1000;
    List<String> jointWinners = computeJointWinners(riskRows, nPoorU);

    return ErrorAttributionResult.builder()
        .trackBInsufficientData(insufficient)
        .nPoorInU(nPoorU)
        .nU(nU)
        .riskRatios(riskRows)
        .jointWinners(jointWinners)
        .analysisPhase("1")
        .track("B")
        .diagnosticSpecVersion(SPEC_VERSION)
        .disclaimer(DISCLAIMER)
        .cachedAt(null)
        .build();
  }

  private static RiskRatioRow buildRiskRow(
      String signal, long nTreated, long nControl, long nTreatedLow, long nControlLow) {
    Double p1 = nTreated == 0 ? null : (double) nTreatedLow / nTreated;
    Double p2 = nControl == 0 ? null : (double) nControlLow / nControl;

    Double rr = null;
    boolean rrUndefined = true;
    String rrReason = null;

    if (nTreated == 0) {
      rrReason = ErrorAttributionResult.RR_EMPTY_TREATED_ARM;
    } else if (nControl == 0) {
      rrReason = ErrorAttributionResult.RR_EMPTY_CONTROL_ARM;
    } else if (p2 != null && p2 > 0) {
      rr = p1 / p2;
      rrUndefined = false;
      rrReason = null;
    } else if (p1 != null && p1 > 0) {
      rrReason = ErrorAttributionResult.RR_INFINITE_RR;
    } else {
      rrReason = ErrorAttributionResult.RR_ZERO_POOR;
    }

    return RiskRatioRow.builder()
        .signal(signal)
        .nTreated(nTreated)
        .nControl(nControl)
        .nTreatedLow(nTreatedLow)
        .nControlLow(nControlLow)
        .p1(p1)
        .p2(p2)
        .rr(rrUndefined ? null : rr)
        .rrUndefined(rrUndefined)
        .rrUndefinedReason(rrReason)
        .build();
  }

  private static List<String> computeJointWinners(List<RiskRatioRow> riskRows, long nPoorInU) {
    if (nPoorInU < 1000) {
      return null;
    }
    double maxKey = Double.NEGATIVE_INFINITY;
    double[] keys = new double[riskRows.size()];
    for (int i = 0; i < riskRows.size(); i++) {
      double k = winnerComparableKey(riskRows.get(i));
      keys[i] = k;
      if (!Double.isNaN(k) && k > maxKey) {
        maxKey = k;
      }
    }
    if (maxKey == Double.NEGATIVE_INFINITY) {
      return null;
    }
    List<String> winners = new ArrayList<>();
    for (int i = 0; i < riskRows.size(); i++) {
      if (!Double.isNaN(keys[i]) && Double.compare(keys[i], maxKey) == 0) {
        winners.add(riskRows.get(i).getSignal());
      }
    }
    return winners.isEmpty() ? null : winners;
  }

  /**
   * Finite RR uses batch-style 4dp rounding; {@code INFINITE_RR} compares as {@link Double#POSITIVE_INFINITY}.
   */
  private static double winnerComparableKey(RiskRatioRow row) {
    if (Boolean.FALSE.equals(row.getRrUndefined()) && row.getRr() != null) {
      return round4(row.getRr());
    }
    if (Boolean.TRUE.equals(row.getRrUndefined())
        && ErrorAttributionResult.RR_INFINITE_RR.equals(row.getRrUndefinedReason())) {
      return Double.POSITIVE_INFINITY;
    }
    return Double.NaN;
  }

  private static double round4(double v) {
    return BigDecimal.valueOf(v).setScale(RR_SCALE, RoundingMode.HALF_UP).doubleValue();
  }

  private static Map<String, Object> lowerKeyMap(Map<String, Object> row) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : row.entrySet()) {
      m.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
    }
    return m;
  }

  private List<Map<String, Object>> rowsToMaps(GetQueryDataResponseDto<GetRawUserEventsResponseDto> response) {
    if (!response.isJobComplete() || response.getData() == null) {
      return List.of();
    }
    GetRawUserEventsResponseDto data = response.getData();
    List<String> names =
        data.getSchema().getFields().stream().map(GetRawUserEventsResponseDto.Field::getName).toList();
    List<Map<String, Object>> out = new ArrayList<>();
    for (GetRawUserEventsResponseDto.Row row : data.getRows()) {
      Map<String, Object> m = new LinkedHashMap<>();
      for (int i = 0; i < names.size(); i++) {
        Object v = i < row.getRowFields().size() ? row.getRowFields().get(i).getValue() : null;
        m.put(names.get(i), v);
      }
      out.add(m);
    }
    return out;
  }
}
