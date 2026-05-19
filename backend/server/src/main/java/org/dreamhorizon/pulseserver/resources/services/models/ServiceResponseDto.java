package org.dreamhorizon.pulseserver.resources.services.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceResponseDto {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("serviceName")
  private String serviceName;

  @JsonProperty("serviceGroup")
  private String serviceGroup;

  @JsonProperty("displayName")
  private String displayName;

  @JsonProperty("ownerEmail")
  private String ownerEmail;

  @JsonProperty("ownerSlackId")
  private String ownerSlackId;

  @JsonProperty("goalertServiceId")
  private String goalertServiceId;

  @JsonProperty("description")
  private String description;

  @JsonProperty("createdAt")
  private String createdAt;

  @JsonProperty("updatedAt")
  private String updatedAt;
}
