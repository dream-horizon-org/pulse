package org.dreamhorizon.pulseserver.client.query.models;

import io.vertx.core.json.JsonArray;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResultSet {
  private JsonArray resultData;
  private String nextToken;
}

