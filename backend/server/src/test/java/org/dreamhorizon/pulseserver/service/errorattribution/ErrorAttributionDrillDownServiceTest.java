package org.dreamhorizon.pulseserver.service.errorattribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownResult.IssueRow;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownResult.NetworkEndpointRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ErrorAttributionDrillDownServiceTest {

  private static final String PROJECT = "p1";
  private static final String INTERACTION = "checkout";
  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant END = Instant.parse("2026-01-08T00:00:00Z");

  @Mock private ClickhouseQueryService clickhouseQueryService;

  private RootCauseConfig configWithTemporalOn;
  private RootCauseConfig configWithTemporalOff;

  private ErrorAttributionDrillDownService serviceOn;
  private ErrorAttributionDrillDownService serviceOff;

  @BeforeEach
  void setUp() {
    configWithTemporalOn =
        RootCauseConfig.withDefaults(
            RootCauseConfig.builder().issueMustPrecedePoor(true).build());
    configWithTemporalOff =
        RootCauseConfig.withDefaults(
            RootCauseConfig.builder().issueMustPrecedePoor(false).build());
    serviceOn = new ErrorAttributionDrillDownService(clickhouseQueryService, configWithTemporalOn);
    serviceOff = new ErrorAttributionDrillDownService(clickhouseQueryService, configWithTemporalOff);
  }

  private static GetQueryDataResponseDto<GetRawUserEventsResponseDto> chResponse(
      boolean jobComplete, GetRawUserEventsResponseDto data) {
    return GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
        .jobComplete(jobComplete)
        .data(data)
        .build();
  }

  private static GetQueryDataResponseDto<GetRawUserEventsResponseDto> tableWithRows(
      List<String> columnNames, List<List<Object>> rowValues) {
    List<GetRawUserEventsResponseDto.Field> fields = new ArrayList<>();
    for (String name : columnNames) {
      GetRawUserEventsResponseDto.Field f = new GetRawUserEventsResponseDto.Field();
      f.setName(name);
      fields.add(f);
    }
    List<GetRawUserEventsResponseDto.Row> rows = new ArrayList<>();
    for (List<Object> values : rowValues) {
      List<GetRawUserEventsResponseDto.RowField> rfs = new ArrayList<>();
      for (Object v : values) {
        GetRawUserEventsResponseDto.RowField rf = new GetRawUserEventsResponseDto.RowField();
        rf.setValue(v);
        rfs.add(rf);
      }
      GetRawUserEventsResponseDto.Row row = new GetRawUserEventsResponseDto.Row();
      row.setRowFields(rfs);
      rows.add(row);
    }
    GetRawUserEventsResponseDto data =
        GetRawUserEventsResponseDto.builder()
            .schema(new GetRawUserEventsResponseDto.Schema(fields))
            .rows(rows)
            .build();
    return chResponse(true, data);
  }

  private static Map<String, Object> issueRowMap(String groupId, String title, String exceptionType) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("GROUP_ID", groupId);
    m.put("Title", title);
    m.put("n_treated", 10L);
    m.put("n_control", 90L);
    m.put("n_treated_low", 2L);
    m.put("n_control_low", 8L);
    if (exceptionType != null) {
      m.put("EXCEPTION_TYPE", exceptionType);
    }
    return m;
  }

  @Test
  void rowsToMaps_shouldYieldEmptyRowsWhenJobIncomplete() {
    when(clickhouseQueryService.executeRootCauseQuery(
            eq(PROJECT), anyString(), anyList(), anyList(), eq(true)))
        .thenReturn(Single.just(chResponse(false, null)));

    ErrorAttributionDrillDownResult r =
        serviceOn.getDrillDown(PROJECT, INTERACTION, START, END, ErrorAttributionDrillDownSignal.crash)
            .blockingGet();

    assertThat(r.getIssues()).isEmpty();
  }

  @Test
  void rowsToMaps_shouldYieldEmptyRowsWhenDataNull() {
    when(clickhouseQueryService.executeRootCauseQuery(
            eq(PROJECT), anyString(), anyList(), anyList(), eq(true)))
        .thenReturn(Single.just(chResponse(true, null)));

    ErrorAttributionDrillDownResult r =
        serviceOn.getDrillDown(PROJECT, INTERACTION, START, END, ErrorAttributionDrillDownSignal.crash)
            .blockingGet();

    assertThat(r.getIssues()).isEmpty();
  }

  @Test
  void rowsToMaps_shouldPadMissingTrailingRowFieldsWithNull() {
    GetRawUserEventsResponseDto.Field fg = new GetRawUserEventsResponseDto.Field();
    fg.setName("group_id");
    GetRawUserEventsResponseDto.Field ft = new GetRawUserEventsResponseDto.Field();
    ft.setName("title");
    GetRawUserEventsResponseDto.Field f1 = new GetRawUserEventsResponseDto.Field();
    f1.setName("n_treated");
    GetRawUserEventsResponseDto.Field f2 = new GetRawUserEventsResponseDto.Field();
    f2.setName("n_control");
    GetRawUserEventsResponseDto.Field f3 = new GetRawUserEventsResponseDto.Field();
    f3.setName("n_treated_low");
    GetRawUserEventsResponseDto.Field f4 = new GetRawUserEventsResponseDto.Field();
    f4.setName("n_control_low");
    GetRawUserEventsResponseDto.Row row = new GetRawUserEventsResponseDto.Row();
    GetRawUserEventsResponseDto.RowField r0 = new GetRawUserEventsResponseDto.RowField();
    r0.setValue("gid");
    GetRawUserEventsResponseDto.RowField r1 = new GetRawUserEventsResponseDto.RowField();
    r1.setValue("Row title");
    GetRawUserEventsResponseDto.RowField r2 = new GetRawUserEventsResponseDto.RowField();
    r2.setValue(10L);
    GetRawUserEventsResponseDto.RowField r3 = new GetRawUserEventsResponseDto.RowField();
    r3.setValue(90L);
    GetRawUserEventsResponseDto.RowField r4 = new GetRawUserEventsResponseDto.RowField();
    r4.setValue(2L);
    row.setRowFields(List.of(r0, r1, r2, r3, r4));
    GetRawUserEventsResponseDto data =
        GetRawUserEventsResponseDto.builder()
            .schema(new GetRawUserEventsResponseDto.Schema(List.of(fg, ft, f1, f2, f3, f4)))
            .rows(List.of(row))
            .build();
    when(clickhouseQueryService.executeRootCauseQuery(
            eq(PROJECT), anyString(), anyList(), anyList(), eq(true)))
        .thenReturn(Single.just(chResponse(true, data)));

    ErrorAttributionDrillDownResult r =
        serviceOn.getDrillDown(PROJECT, INTERACTION, START, END, ErrorAttributionDrillDownSignal.crash)
            .blockingGet();

    assertThat(r.getIssues()).hasSize(1);
    IssueRow issue = r.getIssues().get(0);
    assertThat(issue.getGroupId()).isEqualTo("gid");
    assertThat(issue.getTitle()).isEqualTo("Row title");
    assertThat(issue.getNControlLow()).isEqualTo(0L);
  }

  @Test
  void mapStack_shouldMapCrashRowsWithTemporalRuleWhenEnabled() {
    var resp =
        tableWithRows(
            List.of(
                "group_id",
                "title",
                "n_treated",
                "n_control",
                "n_treated_low",
                "n_control_low"),
            List.of(
                List.of("g1", "CrashTitle", 10L, 90L, 2L, 8L),
                List.of("g2", "Other", 5L, 95L, 1L, 9L)));
    when(clickhouseQueryService.executeRootCauseQuery(
            eq(PROJECT), anyString(), anyList(), anyList(), eq(true)))
        .thenReturn(Single.just(resp));

    ErrorAttributionDrillDownResult r =
        serviceOn.getDrillDown(PROJECT, INTERACTION, START, END, ErrorAttributionDrillDownSignal.crash)
            .blockingGet();

    assertThat(r.getSignal()).isEqualTo("crash");
    assertThat(r.getEligibility()).isEqualTo(ErrorAttributionDrillDownService.ELIGIBILITY_MODE_A_FULL_U);
    assertThat(r.getTemporalRule())
        .isEqualTo(ErrorAttributionDrillDownResult.TEMPORAL_RULE_ISSUE_BEFORE_POOR);
    assertThat(r.getIssues()).hasSize(2);
    assertThat(r.getIssues().get(0).getGroupId()).isEqualTo("g1");
    assertThat(r.getNetworkEndpoints()).isNull();
  }

  @Test
  void mapStack_shouldOmitTemporalRuleWhenDisabled() {
    var resp =
        tableWithRows(
            List.of("group_id", "title", "n_treated", "n_control", "n_treated_low", "n_control_low"),
            List.of(List.of("g1", "T", 10L, 90L, 2L, 8L)));
    when(clickhouseQueryService.executeRootCauseQuery(
            eq(PROJECT), anyString(), anyList(), anyList(), eq(true)))
        .thenReturn(Single.just(resp));

    ErrorAttributionDrillDownResult r =
        serviceOff.getDrillDown(PROJECT, INTERACTION, START, END, ErrorAttributionDrillDownSignal.anr)
            .blockingGet();

    assertThat(r.getSignal()).isEqualTo("anr");
    assertThat(r.getTemporalRule()).isNull();
    assertThat(r.getIssues()).hasSize(1);
  }

  @Test
  void mapStack_shouldMapNonFatalWithExceptionType() {
    Map<String, Object> m = issueRowMap("g", "T", "java.lang.IllegalStateException");
    var resp =
        tableWithRows(
            new ArrayList<>(m.keySet()),
            List.of(new ArrayList<>(m.values())));
    when(clickhouseQueryService.executeRootCauseQuery(
            eq(PROJECT), anyString(), anyList(), anyList(), eq(true)))
        .thenReturn(Single.just(resp));

    ErrorAttributionDrillDownResult r =
        serviceOn.getDrillDown(
                PROJECT, INTERACTION, START, END, ErrorAttributionDrillDownSignal.non_fatal)
            .blockingGet();

    IssueRow issue = r.getIssues().get(0);
    assertThat(issue.getExceptionType()).isEqualTo("java.lang.IllegalStateException");
  }

  @Test
  void mapStack_shouldMapNullExceptionTypeToNullForNonFatal() {
    Map<String, Object> m = issueRowMap("g", "T", null);
    m.put("exception_type", "");
    var resp =
        tableWithRows(
            new ArrayList<>(m.keySet()),
            List.of(new ArrayList<>(m.values())));
    when(clickhouseQueryService.executeRootCauseQuery(
            eq(PROJECT), anyString(), anyList(), anyList(), eq(true)))
        .thenReturn(Single.just(resp));

    ErrorAttributionDrillDownResult r =
        serviceOn.getDrillDown(
                PROJECT, INTERACTION, START, END, ErrorAttributionDrillDownSignal.non_fatal)
            .blockingGet();

    assertThat(r.getIssues().get(0).getExceptionType()).isNull();
  }

  @Test
  void mapApi_shouldMapNetworkEndpointRows() {
    var resp =
        tableWithRows(
            List.of(
                "url",
                "graphql_operation_name",
                "graphql_operation_type",
                "n_treated",
                "n_control",
                "n_treated_low",
                "n_control_low"),
            List.of(
                Arrays.asList(
                    null,
                    "GetUser",
                    "query",
                    3L,
                    97L,
                    1L,
                    20L)));
    when(clickhouseQueryService.executeRootCauseQuery(
            eq(PROJECT), anyString(), anyList(), anyList(), eq(true)))
        .thenReturn(Single.just(resp));

    ErrorAttributionDrillDownResult r =
        serviceOn.getDrillDown(PROJECT, INTERACTION, START, END, ErrorAttributionDrillDownSignal.api)
            .blockingGet();

    assertThat(r.getSignal()).isEqualTo("api");
    assertThat(r.getIssues()).isNull();
    assertThat(r.getNetworkEndpoints()).hasSize(1);
    NetworkEndpointRow ep = r.getNetworkEndpoints().get(0);
    assertThat(ep.getUrl()).isEmpty();
    assertThat(ep.getGraphqlOperationName()).isEqualTo("GetUser");
    assertThat(ep.getGraphqlOperationType()).isEqualTo("query");
    assertThat(ep.getOccurrences()).isEqualTo(3L);
  }

  @Test
  @SuppressWarnings("unchecked")
  void query_shouldForwardProjectAndBindsToClickHouse() {
    when(clickhouseQueryService.executeRootCauseQuery(
            eq(PROJECT), anyString(), anyList(), anyList(), eq(true)))
        .thenReturn(
            Single.just(
                tableWithRows(
                    List.of("group_id", "title", "n_treated", "n_control", "n_treated_low", "n_control_low"),
                    List.of(List.of("g", "t", 1L, 99L, 0L, 10L)))));

    serviceOn.getDrillDown(PROJECT, INTERACTION, START, END, ErrorAttributionDrillDownSignal.crash)
        .blockingGet();

    ArgumentCaptor<List<String>> namesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<Object>> valuesCaptor = ArgumentCaptor.forClass(List.class);
    verify(clickhouseQueryService)
        .executeRootCauseQuery(
            eq(PROJECT), anyString(), namesCaptor.capture(), valuesCaptor.capture(), eq(true));
    assertThat(namesCaptor.getValue()).isNotEmpty();
    assertThat(valuesCaptor.getValue()).hasSameSizeAs(namesCaptor.getValue());
  }
}
