package org.dreamhorizon.pulseserver.dto.v1.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Metadata {
  private int id;
  private String status;
  private String name;
  private JobPlan jobPlan;
}
