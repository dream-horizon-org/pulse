package org.dreamhorizon.pulseserver.service.breadcrumb;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;

public interface BreadcrumbService {
  Single<QueryJob> getSessionBreadcrumbs(String sessionId, String errorTimestamp, String userEmail);
}
