package in.horizonos.pulseserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SuggestedQueryDetails {
  @JsonProperty("query")
  public String query;

  @JsonProperty("queryName")
  public String queryName;

  @JsonProperty("description")
  public String description;
}
