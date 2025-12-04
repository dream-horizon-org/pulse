package org.dreamhorizon.pulseserver.dto.v1.request.incident;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.DefaultValue;

@Getter
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateIncidentRequestDto {
    @JsonProperty("incident_severity")
    @DefaultValue("5")
    public Integer incidentSeverity;

    @DefaultValue("app-uptime-on-call")
    @JsonProperty("service_name")
    public String serviceName;

    @NotNull
    @JsonProperty("description")
    public String description;

    @JsonProperty("slack_channel")
    @DefaultValue("alert_app_critical_interaction")
    public String slackChannel;

    @JsonProperty("roster")
    @DefaultValue("temp-app-uptime_schedule")
    public String roster;

    @NotNull
    @JsonProperty("webhook_url")
    public String webhook_url;
}
