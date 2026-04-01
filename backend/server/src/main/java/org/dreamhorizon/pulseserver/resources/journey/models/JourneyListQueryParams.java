package org.dreamhorizon.pulseserver.resources.journey.models;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import lombok.Data;

@Data
public class JourneyListQueryParams {

  @QueryParam("status")
  private List<String> status;

  @QueryParam("journeyType")
  private String journeyType;

  @QueryParam("search")
  private String search;

  @QueryParam("searchMode")
  @DefaultValue("fts")
  private String searchMode;

  @QueryParam("updatedAfter")
  private String updatedAfter;

  @QueryParam("updatedBefore")
  private String updatedBefore;

  @QueryParam("createdBy")
  private String createdBy;

  @QueryParam("limit")
  @DefaultValue("50")
  private int limit;

  @QueryParam("offset")
  @DefaultValue("0")
  private int offset;
}
