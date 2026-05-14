package org.dreamhorizon.pulseserver.resources.webvitals;

import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalByScreenRow;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalSummaryRow;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalTrendRow;

/**
 * Maps Web Vitals DAO row models to REST DTOs.
 */
public final class WebVitalsMapper {

  private static final double PERCENT_SCALE = 100.0;

  private WebVitalsMapper() { }

  /** Builds {@link VitalSummaryDto} from an aggregated summary row. */
  public static VitalSummaryDto toVitalSummaryDto(final WebVitalSummaryRow row) {
    long totalCount = parseLong(row.getTotalCount());
    long goodCount = parseLong(row.getGoodCount());
    long needsImprovementCount = parseLong(row.getNeedsImprovementCount());
    long poorCount = parseLong(row.getPoorCount());

    double goodPct = goodCount * PERCENT_SCALE / totalCount;
    double needsImprovementPct = needsImprovementCount * PERCENT_SCALE / totalCount;
    double poorPct = poorCount * PERCENT_SCALE / totalCount;

    return VitalSummaryDto.builder()
        .name(row.getVitalName())
        .p75(parseDouble(row.getP75()))
        .goodPct(goodPct)
        .needsImprovementPct(needsImprovementPct)
        .poorPct(poorPct)
        .totalCount(totalCount)
        .build();
  }

  /** Builds {@link TrendPointDto} from a trend bucket row. */
  public static TrendPointDto toTrendPointDto(final WebVitalTrendRow row) {
    return TrendPointDto.builder()
        .bucket(row.getBucket())
        .p75(parseDouble(row.getP75()))
        .build();
  }

  /** Builds {@link ScreenVitalDto} from a per-screen aggregate row. */
  public static ScreenVitalDto toScreenVitalDto(final WebVitalByScreenRow row) {
    return ScreenVitalDto.builder()
        .screenName(row.getScreenName())
        .p75(parseDouble(row.getP75()))
        .totalCount(parseLong(row.getTotalCount()))
        .goodPct(parseDouble(row.getGoodPct()))
        .build();
  }

  private static Double parseDouble(final String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    if ("NaN".equals(value)) {
      return null;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Long parseLong(final String value) {
    if (value == null || value.isEmpty()) {
      return 0L;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return 0L;
    }
  }
}
