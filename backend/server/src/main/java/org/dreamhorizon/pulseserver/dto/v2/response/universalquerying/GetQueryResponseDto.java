package org.dreamhorizon.pulseserver.dto.v2.response.universalquerying;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetQueryResponseDto {
    @JsonProperty("query")
    public String query;

    public static GetQueryResponseDto from(String query) {
        return GetQueryResponseDto.builder()
                .query(query)
                .build();
    }
}
