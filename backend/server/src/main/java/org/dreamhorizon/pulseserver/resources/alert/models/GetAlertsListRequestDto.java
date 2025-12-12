package org.dreamhorizon.pulseserver.resources.alert.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetAlertsListRequestDto {
    @QueryParam("name")
    String name;

    @QueryParam("scope")
    String scope;

    @QueryParam("created_by")
    String createdBy;

    @QueryParam("updated_by")
    String updatedBy;

    @QueryParam("limit")
    @DefaultValue("10")
    Integer limit;

    @QueryParam("offset")
    @DefaultValue("0")
    Integer offset;
}
