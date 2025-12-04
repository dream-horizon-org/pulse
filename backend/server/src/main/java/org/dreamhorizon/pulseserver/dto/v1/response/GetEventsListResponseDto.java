package org.dreamhorizon.pulseserver.dto.v1.response;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class GetEventsListResponseDto {
    @JsonProperty("data")
    private GetEventsListSuccessResponseDto data;

    @JsonProperty("error")
    private ErrorResponseDto error;

    public boolean isError() {
        return error != null;
    }
}
