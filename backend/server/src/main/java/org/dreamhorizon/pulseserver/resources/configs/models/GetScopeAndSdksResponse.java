package org.dreamhorizon.pulseserver.resources.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GetScopeAndSdksResponse {
  @JsonProperty("scope")
  List<String> scope;

  @JsonProperty("sdks")
  List<String> sdks;
}
