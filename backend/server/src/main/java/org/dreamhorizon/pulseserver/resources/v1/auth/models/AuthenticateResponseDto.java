package org.dreamhorizon.pulseserver.resources.v1.auth.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.dto.ProjectSummaryDto;

import java.util.List;

@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticateResponseDto {
  @JsonProperty("accessToken")
  private String accessToken;

  @JsonProperty("expiresIn")
  private Integer expiresIn;

  @JsonProperty("idToken")
  private String idToken;

  @JsonProperty("refreshToken")
  private String refreshToken;

  @JsonProperty("tokenType")
  private String tokenType;
  
  // New fields for enhanced auth flow
  @JsonProperty("userId")
  private String userId;
  
  @JsonProperty("tenantId")
  private String tenantId;
  
  @JsonProperty("needsOnboarding")
  private Boolean needsOnboarding;
  
  @JsonProperty("projects")
  private List<ProjectSummaryDto> projects;
}
