package org.dreamhorizon.pulseserver.dto.v2.request.userevents;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.QueryParam;

@Data
public class QueryRequestIdEventRequest {

    @NotNull
    @HeaderParam("authorization")
    private String authorization;

    @QueryParam("userId")
    private long userId;

    @QueryParam("from_date")
    private String fromDate;

    @QueryParam("to_date")
    private String toDate;

    @QueryParam("email")
    private String email;

    @QueryParam("pattern")
    private String pattern;
}

