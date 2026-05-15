package org.dreamhorizon.pulseserver.resources.webvitals.models;

import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class WebVitalsByScreenQueryParams {

  @QueryParam("startTime")
  private String startTime;

  @QueryParam("endTime")
  private String endTime;

  @QueryParam("vitalName")
  private String vitalName;
}
