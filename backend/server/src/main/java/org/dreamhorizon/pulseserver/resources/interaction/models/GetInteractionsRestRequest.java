package org.dreamhorizon.pulseserver.resources.interaction.models;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetInteractionsRestRequest {
  @QueryParam("page")
  @DefaultValue("0")
  private Integer page;

  @QueryParam("size")
  @DefaultValue("10")
  private Integer size;

  @QueryParam("name")
  private String name;

  @QueryParam("userEmail")
  private String userEmail;

  @QueryParam("status")
  private String status;
}
