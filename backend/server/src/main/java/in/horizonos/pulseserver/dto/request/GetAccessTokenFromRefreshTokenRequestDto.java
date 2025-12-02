package in.horizonos.pulseserver.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetAccessTokenFromRefreshTokenRequestDto {
  @JsonProperty("refreshToken")
  @NotNull
  public String refreshToken;
}
