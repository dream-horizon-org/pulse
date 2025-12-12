package org.dreamhorizon.pulseserver.dto.v2.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetFiltersResponseDto {
  private List<String> users;
  private Map<String, String> statuses;
}
