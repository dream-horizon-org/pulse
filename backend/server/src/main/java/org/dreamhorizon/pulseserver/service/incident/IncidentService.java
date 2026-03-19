package org.dreamhorizon.pulseserver.service.incident;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentRequestDto;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentResponseDto;
import org.dreamhorizon.pulseserver.resources.incident.models.IncidentResponseDto;

public interface IncidentService {

  Single<List<IncidentResponseDto>> getIncidents();

  Single<CreateIncidentResponseDto> createIncident(CreateIncidentRequestDto request);

  Completable acknowledgeIncident(long incidentId, String actionBy);

  Completable recoverIncident(long incidentId, String actionBy);

  Completable closeIncident(long incidentId, String actionBy);
}
