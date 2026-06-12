package org.dreamhorizon.pulseserver.resources.v1.auth.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticateRequestDto {

  @NotNull
  @JsonProperty("responseType")
  public String responseType;

  @NotNull
  @JsonProperty("grantType")
  public String grantType;

  @NotNull
  @JsonProperty("identifier")
  public String identifier;

  @NotNull
  @JsonProperty("idProvider")
  public String idProvider;

  @NotNull
  @JsonProperty("resources")
  public String[] resources;
}
