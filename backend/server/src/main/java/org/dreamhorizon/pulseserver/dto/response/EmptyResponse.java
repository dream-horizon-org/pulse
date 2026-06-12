package org.dreamhorizon.pulseserver.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmptyResponse {
  public static final EmptyResponse emptyResponse = new EmptyResponse();
}
