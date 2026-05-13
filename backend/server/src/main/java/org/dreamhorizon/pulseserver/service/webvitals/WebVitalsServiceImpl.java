package org.dreamhorizon.pulseserver.service.webvitals;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.webvitals.WebVitalsDao;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalByScreenRow;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalSummaryRow;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalTrendRow;
import org.dreamhorizon.pulseserver.resources.webvitals.WebVitalsByScreenResponseDto;
import org.dreamhorizon.pulseserver.resources.webvitals.WebVitalsMapper;
import org.dreamhorizon.pulseserver.resources.webvitals.WebVitalsSummaryResponseDto;
import org.dreamhorizon.pulseserver.resources.webvitals.WebVitalsTrendResponseDto;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class WebVitalsServiceImpl implements WebVitalsService {

  private final WebVitalsDao webVitalsDao;

  @Override
  public Single<WebVitalsSummaryResponseDto> getSummary(
      Instant startTime, Instant endTime, String screenName) {
    return webVitalsDao
        .getSummary(startTime, endTime, screenName)
        .map(
            rows ->
                rows.stream()
                    .filter(this::isValidSummaryRow)
                    .map(WebVitalsMapper::toVitalSummaryDto)
                    .collect(Collectors.toList()))
        .map(vitals -> WebVitalsSummaryResponseDto.builder().vitals(vitals).build());
  }

  @Override
  public Single<WebVitalsTrendResponseDto> getTrend(
      Instant startTime, Instant endTime, String vitalName, int bucketMinutes, String screenName) {
    return webVitalsDao
        .getTrend(startTime, endTime, vitalName, bucketMinutes, screenName)
        .map(
            rows ->
                rows.stream()
                    .filter(this::isValidTrendRow)
                    .map(WebVitalsMapper::toTrendPointDto)
                    .collect(Collectors.toList()))
        .map(points -> WebVitalsTrendResponseDto.builder().points(points).build());
  }

  @Override
  public Single<WebVitalsByScreenResponseDto> getByScreen(
      Instant startTime, Instant endTime, String vitalName) {
    return webVitalsDao
        .getByScreen(startTime, endTime, vitalName)
        .map(
            rows ->
                rows.stream()
                    .filter(this::isValidByScreenRow)
                    .map(WebVitalsMapper::toScreenVitalDto)
                    .collect(Collectors.toList()))
        .map(screens -> WebVitalsByScreenResponseDto.builder().screens(screens).build());
  }

  private boolean isValidSummaryRow(WebVitalSummaryRow row) {
    long totalCount = parseLong(row.getTotalCount());
    if (totalCount == 0) {
      return false;
    }
    return parseDouble(row.getP75()) != null;
  }

  private boolean isValidTrendRow(WebVitalTrendRow row) {
    return parseDouble(row.getP75()) != null;
  }

  private boolean isValidByScreenRow(WebVitalByScreenRow row) {
    return parseDouble(row.getP75()) != null;
  }

  private Double parseDouble(String value) {
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

  private Long parseLong(String value) {
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
