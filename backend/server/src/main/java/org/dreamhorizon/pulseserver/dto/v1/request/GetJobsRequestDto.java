package org.dreamhorizon.pulseserver.dto.v1.request;

import java.util.List;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetJobsRequestDto {
  @QueryParam("page")
  @DefaultValue("0")
  private String page;

  @QueryParam("size")
  @DefaultValue("10")
  private String size;

  @QueryParam("name")
  private String name;

  @QueryParam("users")
  private String users;

  @QueryParam("statuses")
  private String statuses;

  @QueryParam("resources")
  private String resources;

  @QueryParam("slack")
  private String slack;

  @QueryParam("tags")
  private String tags;
}
