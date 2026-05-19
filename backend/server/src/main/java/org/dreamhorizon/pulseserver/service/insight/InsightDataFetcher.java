package org.dreamhorizon.pulseserver.service.insight;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;

public interface InsightDataFetcher {

  /** Date-range mode: fetch and aggregate data for one calendar day. */
  Single<JsonNode> fetchForDate(String projectId, String entityKey, LocalDate date);

  /** Real-time mode: fetch live aggregated data (fetcher decides window, e.g. last 24h). */
  Single<JsonNode> fetchLive(String projectId, String entityKey);
}
