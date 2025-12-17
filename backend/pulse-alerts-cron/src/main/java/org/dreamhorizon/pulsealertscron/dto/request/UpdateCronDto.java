package org.dreamhorizon.pulsealertscron.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateCronDto {
  @NotNull
  @JsonProperty(value = "id")
  private Integer id;

  @NotNull
  @JsonProperty(value = "url")
  private String url;

  @NotNull
  @JsonProperty(value = "newInterval")
  private Integer newInterval;

  @NotNull
  @JsonProperty(value = "oldInterval")
  private Integer oldInterval;
}
