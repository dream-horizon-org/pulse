package org.dreamhorizon.pulseserver.dto.v2.response.universalquerying;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetUserQueryResponseDto {
    @JsonProperty("queries")
    private List<String> queries;

    public static GetUserQueryResponseDto from(List<String> queries) {
        return GetUserQueryResponseDto.builder()
            .queries(queries)
            .build();
    }
}
