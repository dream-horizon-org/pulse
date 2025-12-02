package in.horizonos.pulseserver.resources.v1.auth.models;

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
public class AuthenticateResponseDto {
  @JsonProperty("accessToken")
  private String accessToken;

  @JsonProperty("expiresIn")
  private Integer expiresIn;

  @JsonProperty("idToken")
  private String idToken;

  @JsonProperty("refreshToken")
  private String refreshToken;

  @JsonProperty("tokenType")
  private String tokenType;
}
