package in.horizonos.pulseserver.dto.response.universalquerying;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateQueryResponseDto {
  public boolean success;
  public String errorMessage;
}
