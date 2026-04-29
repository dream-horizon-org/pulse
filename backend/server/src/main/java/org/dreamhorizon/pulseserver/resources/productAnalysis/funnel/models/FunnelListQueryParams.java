package org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

import java.util.List;

import lombok.Data;

@Data
public class FunnelListQueryParams {

  @QueryParam("status")
  private List<String> status;

  @QueryParam("funnelType")
  private String funnelType;

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

  @QueryParam("page")
  @DefaultValue("1")
  private int page;

  @QueryParam("pageSize")
  @DefaultValue("10")
  private int pageSize;

  @QueryParam("limit")
  @DefaultValue("50")
  private int limit;

  @QueryParam("offset")
  @DefaultValue("0")
  private int offset;
}
