package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionEvidenceResult {

  /** List of poor sessions (just session IDs). */
  private List<EvidenceSession> sessions;

  /** Total distinct sessions in segment (before limit). */
  private Integer totalSessionsCount;
}
