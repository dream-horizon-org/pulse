package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.dao.AlertsDao;
import org.dreamhorizon.pulseserver.resources.alert.enums.AlertState;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertEvaluationResponseDto {

  AlertsDao.AlertDetails alert;

  @JsonProperty("scopeId")
  Integer scopeId;

  @JsonProperty("evaluationResult")
  String evaluationResult;

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

  @JsonProperty("state")
  AlertState state;
}

