package org.dreamhorizon.pulseserver.dto.v1.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionCheckRequestDto {

  @JsonProperty("id")
  @NotNull
  public String id;

  // Type can be 'alerts' or 'experience'
  @JsonProperty("type")
  @NotNull
  public String type;

  @JsonProperty("userEmail")
  @NotNull
  public String userEmail;

  // Operation can be 'update' or 'delete'
  @JsonProperty("operation")
  @NotNull
  public String operation;
}
