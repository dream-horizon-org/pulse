package org.dreamhorizon.pulseserver.service.productAnalysis.funnel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffCauseRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffEvidenceRow;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDropoffCauseDto;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDropoffEvidenceDto;

/**
 * Maps DAO rows from the drop-off query path into the UI-facing DTOs. Keeps the
 * service impl clean and centralises the {@code exampleSessions} CSV → list split.
 */
public final class FunnelDropoffMapper {

  private FunnelDropoffMapper() {
  }

  public static List<FunnelDropoffCauseDto> fromCauseRows(List<FunnelDropoffCauseRow> rows) {
    if (rows == null || rows.isEmpty()) {
      return Collections.emptyList();
    }
    List<FunnelDropoffCauseDto> out = new ArrayList<>(rows.size());
    for (FunnelDropoffCauseRow r : rows) {
      out.add(toDto(r));
    }
    return out;
  }

  public static List<FunnelDropoffEvidenceDto> fromEvidenceRows(List<FunnelDropoffEvidenceRow> rows) {
    if (rows == null || rows.isEmpty()) {
      return Collections.emptyList();
    }
    List<FunnelDropoffEvidenceDto> out = new ArrayList<>(rows.size());
    for (FunnelDropoffEvidenceRow r : rows) {
      out.add(FunnelDropoffEvidenceDto.builder()
          .sessionId(r.getSessionId())
          .userId(r.getUserId())
          .lastReachedAt(r.getLastReachedAt())
          .traceId(r.getTraceId())
          .screen(r.getScreen())
          .appVersion(r.getAppVersion())
          .platform(r.getPlatform())
          .build());
    }
    return out;
  }

  private static FunnelDropoffCauseDto toDto(FunnelDropoffCauseRow r) {
    long dropoffCohort = nz(r.getDropoffCohort());
    long dropoffAffected = nz(r.getDropoffAffected());
    double dropoffRate = dropoffCohort == 0
        ? 0.0
        : Math.round((dropoffAffected * 1000.0 / dropoffCohort)) / 10.0;
    return FunnelDropoffCauseDto.builder()
        .causeKind(r.getCauseKind())
        .causeKey(r.getCauseKey())
        .causeLabel(r.getCauseLabel())
        .dropoffCohort(dropoffCohort)
        .dropoffAffected(dropoffAffected)
        .converterCohort(nz(r.getConverterCohort()))
        .converterAffected(nz(r.getConverterAffected()))
        .lift(r.getLift() == null ? 0.0 : r.getLift())
        .dropoffRate(dropoffRate)
        .exampleSessionIds(splitCsv(r.getExampleSessions()))
        .build();
  }

  private static long nz(Long v) {
    return v == null ? 0L : v;
  }

  /** Splits comma-separated session IDs from ClickHouse attribution rows. */
  public static List<String> splitCsv(String csv) {
    if (csv == null || csv.isBlank()) {
      return Collections.emptyList();
    }
    List<String> out = new ArrayList<>();
    for (String part : Arrays.asList(csv.split(","))) {
      String s = part.trim();
      if (!s.isEmpty()) {
        out.add(s);
      }
    }
    return out;
  }
}
