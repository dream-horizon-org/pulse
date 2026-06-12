package org.dreamhorizon.pulseserver.client.athena.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.services.athena.model.ResultSet;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultSetWithToken {
  private ResultSet resultSet;
  private String nextToken;
}



