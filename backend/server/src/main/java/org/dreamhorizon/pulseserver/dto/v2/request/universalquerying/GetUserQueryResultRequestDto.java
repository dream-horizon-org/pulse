package org.dreamhorizon.pulseserver.dto.v2.request.universalquerying;

import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetUserQueryResultRequestDto {
    @QueryParam("emailId")
    @NotBlank(message = "email id cannot be blank")
    private String emailId;

    @QueryParam("size")
    @DefaultValue("10")
    private Integer size;
}
