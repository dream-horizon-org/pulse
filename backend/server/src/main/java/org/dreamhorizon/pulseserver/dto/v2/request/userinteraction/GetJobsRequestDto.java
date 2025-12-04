package org.dreamhorizon.pulseserver.dto.v2.request.userinteraction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetJobsRequestDto {
    @QueryParam("page")
    @DefaultValue("0")
    private Integer page;

    @QueryParam("size")
    @DefaultValue("10")
    private Integer size;

    @QueryParam("interactionName")
    private String interactionName;

    @QueryParam("userEmail")
    private String userEmail;
}
