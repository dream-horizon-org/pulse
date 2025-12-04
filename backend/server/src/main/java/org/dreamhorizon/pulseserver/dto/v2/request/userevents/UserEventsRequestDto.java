package org.dreamhorizon.pulseserver.dto.v2.request.userevents;

import lombok.AllArgsConstructor;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.QueryParam;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEventsRequestDto {

  @NotNull
  @HeaderParam("authorization")
  private String authorization;

  @QueryParam("pageToken")
  private String pageToken;

  @QueryParam("requestId")
  private String requestId;
}

