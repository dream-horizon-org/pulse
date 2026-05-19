package org.dreamhorizon.pulseserver.resources.services.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateServiceRequest {

  @NotBlank(message = "Service name is required")
  @Size(max = 128, message = "Service name must not exceed 128 characters")
  @JsonProperty("serviceName")
  private String serviceName;

  @Size(max = 128, message = "Service group must not exceed 128 characters")
  @JsonProperty("serviceGroup")
  private String serviceGroup;

  @Size(max = 255, message = "Display name must not exceed 255 characters")
  @JsonProperty("displayName")
  private String displayName;

  @NotBlank(message = "Owner email is required")
  @Email(message = "Owner email must be a valid email address")
  @JsonProperty("ownerEmail")
  private String ownerEmail;

  @Size(max = 32, message = "Owner Slack ID must not exceed 32 characters")
  @JsonProperty("ownerSlackId")
  private String ownerSlackId;

  @Size(max = 128, message = "GoAlert service ID must not exceed 128 characters")
  @JsonProperty("goalertServiceId")
  private String goalertServiceId;

  @Size(max = 1000, message = "Description must not exceed 1000 characters")
  @JsonProperty("description")
  private String description;
}
