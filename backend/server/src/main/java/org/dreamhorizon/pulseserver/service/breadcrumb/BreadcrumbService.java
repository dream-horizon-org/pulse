package org.dreamhorizon.pulseserver.service.breadcrumb;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.query.models.SubmitQueryResponseDto;

public interface BreadcrumbService {
  Single<SubmitQueryResponseDto> getSessionBreadcrumbs(String sessionId, String errorTimestamp, String userEmail);
}
