package org.dreamhorizon.pulseserver.resources.notification.models;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCreateMappingRequestDto {

  @NotEmpty(message = "mappings list cannot be empty")
  @Valid
  private List<CreateMappingRequestDto> mappings;
}
