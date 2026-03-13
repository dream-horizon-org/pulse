package org.dreamhorizon.pulseserver.service.sessiondetail;

import io.reactivex.rxjava3.core.Single;
import java.util.Set;
import org.dreamhorizon.pulseserver.resources.sessiondetail.models.SessionDetailResponse;

public interface SessionDetailService {

  /**
   * Fetches session detail for the given sessionId.
   * Core data (metadata, interactions, networkRequests) is always returned.
   * Additional sections are included based on {@code includeSections}.
   *
   * @param sessionId       the session identifier
   * @param includeSections set of optional sections: "events", "exceptions"
   * @return the assembled session detail response
   */
  Single<SessionDetailResponse> getSessionDetail(String sessionId, Set<String> includeSections);
}
