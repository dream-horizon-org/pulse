package org.dreamhorizon.pulseserver.resources.webvitals.models;

import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class WebVitalsTrendQueryParams {

  @QueryParam("startTime")
  private String startTime;

  @QueryParam("endTime")
  private String endTime;

  @QueryParam("vitalName")
  private String vitalName;

  @QueryParam("bucketMinutes")
  private Integer bucketMinutes;

  @QueryParam("screenName")
  private String screenName;
}
