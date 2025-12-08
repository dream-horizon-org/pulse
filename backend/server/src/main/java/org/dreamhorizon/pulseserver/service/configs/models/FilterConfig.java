package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.configs.models.FilterMode;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FilterConfig {
  private FilterMode mode;

  private List<EventFilter> whitelist;

  private List<EventFilter> blacklist;
}
