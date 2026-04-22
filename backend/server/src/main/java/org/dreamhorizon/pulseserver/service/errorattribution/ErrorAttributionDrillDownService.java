package org.dreamhorizon.pulseserver.service.errorattribution;

import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownQueryBuilder.DrillDownQueryParams;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownResult.IssueRow;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownResult.NetworkEndpointRow;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionResult.RiskRatioRow;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQuerySpec;
import org.dreamhorizon.pulseserver.util.NumberCoercionUtils;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ErrorAttributionDrillDownService {

  /** Per-issue arms over full universe {@code U} (Mode A); not Poor-touch-only eligibility. */
  public static final String ELIGIBILITY_MODE_A_FULL_U = "mode_a_full_u";

  private final ClickhouseQueryService clickhouseQueryService;
  private final RootCauseConfig rootCauseConfig;

  public Single<ErrorAttributionDrillDownResult> getDrillDown(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive,
      ErrorAttributionDrillDownSignal signal) {
    DrillDownQueryParams params = DrillDownQueryParams.fromRootCauseConfig(rootCauseConfig);
    RootCauseQuerySpec spec =
        ErrorAttributionDrillDownQueryBuilder.build(
            projectId, interactionName, startInclusive, endExclusive, signal, params);
    return clickhouseQueryService
        .executeRootCauseQuery(projectId, spec.sql(), spec.bindNames(), spec.bindValues())
        .map(this::rowsToMaps)
        .map(rows -> mapResult(signal, rows, params));
  }

  /** {@code null} when temporal ordering is off (omitted on REST). */
  private static String temporalRuleFor(DrillDownQueryParams params) {
    return params.issueMustPrecedePoor()
        ? ErrorAttributionDrillDownResult.TEMPORAL_RULE_ISSUE_BEFORE_POOR
        : null;
  }

  private ErrorAttributionDrillDownResult mapResult(
      ErrorAttributionDrillDownSignal signal, List<Map<String, Object>> rows, DrillDownQueryParams params) {
    if (signal == ErrorAttributionDrillDownSignal.api) {
      List<NetworkEndpointRow> endpoints = new ArrayList<>();
      for (Map<String, Object> raw : rows) {
        Map<String, Object> m = lowerKeyMap(raw);
        RiskRatioRow r =
            ErrorAttributionRiskMath.buildRiskRow(
                "",
                NumberCoercionUtils.toLong(m.get("n_treated")),
                NumberCoercionUtils.toLong(m.get("n_control")),
                NumberCoercionUtils.toLong(m.get("n_treated_low")),
                NumberCoercionUtils.toLong(m.get("n_control_low")));
        endpoints.add(
            NetworkEndpointRow.builder()
                .url(stringOrEmpty(m.get("url")))
                .graphqlOperationName(stringOrEmpty(m.get("graphql_operation_name")))
                .graphqlOperationType(stringOrEmpty(m.get("graphql_operation_type")))
                .httpMethod(stringOrEmpty(m.get("http_method")))
                .httpStatusCode(stringOrEmpty(m.get("http_status_code")))
                .occurrences(r.getNTreated())
                .nTreated(r.getNTreated())
                .nControl(r.getNControl())
                .nTreatedLow(r.getNTreatedLow())
                .nControlLow(r.getNControlLow())
                .p1(r.getP1())
                .p2(r.getP2())
                .rr(r.getRr())
                .rrUndefined(r.getRrUndefined())
                .rrUndefinedReason(r.getRrUndefinedReason())
                .build());
      }
      return ErrorAttributionDrillDownResult.builder()
          .signal(signal.name())
          .eligibility(ELIGIBILITY_MODE_A_FULL_U)
          .temporalRule(temporalRuleFor(params))
          .issues(null)
          .networkEndpoints(endpoints)
          .build();
    }

    List<IssueRow> issues = new ArrayList<>();
    for (Map<String, Object> raw : rows) {
      Map<String, Object> m = lowerKeyMap(raw);
      RiskRatioRow r =
          ErrorAttributionRiskMath.buildRiskRow(
              "",
              NumberCoercionUtils.toLong(m.get("n_treated")),
              NumberCoercionUtils.toLong(m.get("n_control")),
              NumberCoercionUtils.toLong(m.get("n_treated_low")),
              NumberCoercionUtils.toLong(m.get("n_control_low")));
      IssueRow.IssueRowBuilder b =
          IssueRow.builder()
              .groupId(stringOrEmpty(m.get("group_id")))
              .title(stringOrEmpty(m.get("title")))
              .occurrences(r.getNTreated())
              .nTreated(r.getNTreated())
              .nControl(r.getNControl())
              .nTreatedLow(r.getNTreatedLow())
              .nControlLow(r.getNControlLow())
              .p1(r.getP1())
              .p2(r.getP2())
              .rr(r.getRr())
              .rrUndefined(r.getRrUndefined())
              .rrUndefinedReason(r.getRrUndefinedReason());
      if (signal == ErrorAttributionDrillDownSignal.non_fatal) {
        b.exceptionType(stringOrNull(m.get("exception_type")));
      }
      issues.add(b.build());
    }
    return ErrorAttributionDrillDownResult.builder()
        .signal(signal.name())
        .eligibility(ELIGIBILITY_MODE_A_FULL_U)
        .temporalRule(temporalRuleFor(params))
        .issues(issues)
        .networkEndpoints(null)
        .build();
  }

  private static String stringOrEmpty(Object v) {
    return v == null ? "" : String.valueOf(v);
  }

  private static String stringOrNull(Object v) {
    if (v == null) {
      return null;
    }
    String s = String.valueOf(v);
    return s.isEmpty() ? null : s;
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
