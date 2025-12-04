package org.dreamhorizon.pulseserver.dto.v2.response.universalquerying;

import org.dreamhorizon.pulseserver.model.SuggestedQueryDetails;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FetchSuggestedQueryResponseDto {
    @JsonProperty("suggestedQuery")
    public List<SuggestedQueryDetails> suggestedQuery;
}
