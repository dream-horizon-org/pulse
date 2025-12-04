package org.dreamhorizon.pulseserver.dto.v2.response.universalquerying;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawGetQueryResultResponseDto {

    @JsonProperty("jobReference")
    private JobReference jobReference;

    @JsonProperty("jobComplete")
    private boolean jobComplete;

    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JobReference {
        @JsonProperty("jobId")
        private String jobId;
    }
}
