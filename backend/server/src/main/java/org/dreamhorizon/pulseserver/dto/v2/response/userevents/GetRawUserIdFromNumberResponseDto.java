package org.dreamhorizon.pulseserver.dto.v2.response.userevents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetRawUserIdFromNumberResponseDto {

  @JsonProperty("userId")
  private Long userId;

}
