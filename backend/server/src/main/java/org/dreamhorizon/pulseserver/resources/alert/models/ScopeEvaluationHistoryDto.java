package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ScopeEvaluationHistoryDto {
    @JsonProperty("scope_id")
    private Integer scopeId;

    @JsonProperty("scope_name")
    private String scopeName;

    @JsonProperty("evaluation_history")
    private List<EvaluationHistoryEntryDto> evaluationHistory;
}

