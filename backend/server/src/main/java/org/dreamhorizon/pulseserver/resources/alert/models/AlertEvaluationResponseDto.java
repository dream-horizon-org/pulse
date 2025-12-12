package org.dreamhorizon.pulseserver.resources.alert.models;

import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.service.alert.core.models.Alert;
import org.dreamhorizon.pulseserver.service.alert.core.models.AlertMetricReading;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertEvaluationResponseDto {

  Alert alert;

  @JsonProperty(Constants.RESULT_SET_KEY)
  List<AlertMetricReading> resultSet;

  @JsonProperty(Constants.ALERT_EVALUATION_QUERY_TIME)
  Long timeTaken;

  @JsonProperty(Constants.ALERT_EVALUATION_START_TIME)
  String evaluationStartTime;

  @JsonProperty(Constants.ALERT_EVALUATION_END_TIME)
  String evaluationEndTime;

  @JsonProperty(Constants.STATUS_KEY)
  String status;

  @JsonProperty(Constants.ERROR_KEY)
  String error;
}

