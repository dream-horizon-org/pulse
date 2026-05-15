package org.dreamhorizon.pulseserver.dao.webvitals.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebVitalTrendRow {

  private String bucket;

  private String p75;
}
