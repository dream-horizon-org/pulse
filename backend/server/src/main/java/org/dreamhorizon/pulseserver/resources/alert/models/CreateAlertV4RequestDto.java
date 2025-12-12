package org.dreamhorizon.pulseserver.dto.v1.request.alerts;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAlertV4RequestDto {
    @NotNull
    @JsonProperty("name")
    private String name;

    @NotNull
    @JsonProperty("description")
    private String description;

    @NotNull
    @JsonProperty("evaluation_period")
    private Integer evaluationPeriod;

    @NotNull
    @JsonProperty("evaluation_interval")
    private Integer evaluationInterval;

    @NotNull
    @JsonProperty("severity_id")
    private Integer severityId;

    @NotNull
    @JsonProperty("notification_channel_id")
    private Integer notificationChannelId;

    @NotNull
    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("additional_condition")
    private String additionalCondition;

    @NotNull
    @Valid
    @JsonProperty("subject")
    private SubjectDto subject;

    @NotNull
    @JsonProperty("condition_expression")
    private String conditionExpression;

    @NotNull
    @Valid
    @JsonProperty("alerts")
    private List<AlertConditionDto> alerts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectDto {
        @NotNull
        @JsonProperty("type")
        private String type;

        @NotNull
        @JsonProperty("name")
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertConditionDto {
        @NotNull
        @JsonProperty("alias")
        private String alias;

        @NotNull
        @JsonProperty("metric")
        private String metric;

        @NotNull
        @JsonProperty("metric_operator")
        private String metricOperator;

        @NotNull
        @JsonProperty("threshold")
        private Map<String, Float> threshold;
    }
}


