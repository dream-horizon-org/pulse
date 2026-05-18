package org.dreamhorizon.pulseserver.service.webvitals;

import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import org.dreamhorizon.pulseserver.resources.webvitals.WebVitalsSummaryResponseDto;
import org.dreamhorizon.pulseserver.resources.webvitals.WebVitalsTrendResponseDto;
import org.dreamhorizon.pulseserver.resources.webvitals.WebVitalsByScreenResponseDto;

public interface WebVitalsService {

  /**
   * Get web vitals summary for a time range.
   *
   * @param startTime start time (inclusive)
   * @param endTime end time (inclusive)
   * @param screenName optional screen name filter; if null, returns global summary
   * @return Single containing WebVitalsSummaryResponseDto with summary stats for each vital
   */
  Single<WebVitalsSummaryResponseDto> getSummary(Instant startTime, Instant endTime, String screenName);

  /**
   * Get web vitals trend for a time range.
   *
   * @param startTime start time (inclusive)
   * @param endTime end time (inclusive)
   * @param vitalName name of the vital (LCP, INP, CLS, etc.)
   * @param bucketMinutes interval in minutes for grouping
   * @param screenName optional screen name filter; if null, returns global trend
   * @return Single containing WebVitalsTrendResponseDto with trend points
   */
  Single<WebVitalsTrendResponseDto> getTrend(
      Instant startTime, Instant endTime, String vitalName, int bucketMinutes, String screenName);

  /**
   * Get web vitals breakdown by screen name for a specific vital.
   *
   * @param startTime start time (inclusive)
   * @param endTime end time (inclusive)
   * @param vitalName name of the vital (LCP, INP, CLS, etc.)
   * @return Single containing WebVitalsByScreenResponseDto with per-screen stats
   */
  Single<WebVitalsByScreenResponseDto> getByScreen(
      Instant startTime, Instant endTime, String vitalName);
}
