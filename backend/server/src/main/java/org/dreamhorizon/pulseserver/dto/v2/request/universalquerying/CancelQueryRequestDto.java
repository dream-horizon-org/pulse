package org.dreamhorizon.pulseserver.dto.v2.request.universalquerying;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CancelQueryRequestDto {
    @JsonProperty("requestId")
    @NotBlank(message = "requestId cannot be blank")
    private String requestId;
}
