package org.dreamhorizon.pulseserver.resources.v1.auth.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    @NotBlank(message = "Firebase ID token is required")
    @JsonProperty("firebaseIdToken")
    private String firebaseIdToken;
}
