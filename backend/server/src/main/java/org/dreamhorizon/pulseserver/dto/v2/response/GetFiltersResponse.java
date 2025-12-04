package org.dreamhorizon.pulseserver.dto.v2.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetFiltersResponse {
  List<String> appVersionCodes;
  List<String> deviceModels;
  List<String> networkProviders;
  List<String> platforms;
  List<String> osVersions;
  List<String> states;
}
