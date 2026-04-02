package org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FunnelDefinitionListParams {
  List<String> statuses;
  String funnelType;
  String nameLikePrefix;
  String ftsBooleanQuery;
  boolean useFullTextSearch;
  Instant updatedAfter;
  Instant updatedBefore;
  String createdBy;
  int limit;
  int offset;
}
