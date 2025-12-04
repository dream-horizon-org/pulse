package org.dreamhorizon.pulseserver.dto.v2.request.userevents;

import lombok.Data;

import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.QueryParam;
import jakarta.validation.constraints.NotNull;

@Data
public class GetEventByNameRequest {

    @NotNull
    @HeaderParam("authorization")
    private String authorization;

    @QueryParam("userId")
    private long userId;

    @QueryParam("eventName")
    private String eventName;

    @QueryParam("eventTimestamp")
    private String eventTimestamp;
}
