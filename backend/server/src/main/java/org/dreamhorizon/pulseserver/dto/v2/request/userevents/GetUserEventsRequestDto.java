package org.dreamhorizon.pulseserver.dto.v2.request.userevents;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUserEventsRequestDto {
    @JsonProperty("phoneNo")
    @NotBlank(message = "phoneNumber cannot be blank")
    private String phoneNo;

    @JsonProperty("fetchTime")
    @NotBlank(message = "fetchTime cannot be blank")
    private String startTime;

}
