package org.dreamhorizon.pulseserver.dao.journey;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JourneyListParams {
  List<String> statuses;
  String journeyType;
  String nameLikePrefix;
  String ftsBooleanQuery;
  boolean useFullTextSearch;
  Instant updatedAfter;
  Instant updatedBefore;
  String createdBy;
  int limit;
  int offset;
}
