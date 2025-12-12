package org.dreamhorizon.pulseserver.dto.v2.request.universalquerying;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetQueryResultRequestDto {
    @JsonProperty("query")
    @NotBlank(message = "query cannot be blank")
    private String query;

    @JsonProperty("emailId")
    @NotBlank(message = "email id cannot be blank")
    private String emailId;
}
