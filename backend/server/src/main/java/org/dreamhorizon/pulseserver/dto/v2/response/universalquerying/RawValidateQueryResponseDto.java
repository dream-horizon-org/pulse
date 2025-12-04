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
public class RawValidateQueryResponseDto {
    @JsonProperty("error")
    private ErrorDetail error;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorDetail {

        @JsonProperty("code")
        private Integer code;

        @JsonProperty("message")
        private String message;
    }
}
