package org.dreamhorizon.pulseserver.service.eventdefinition;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.io.InputStream;
import java.util.List;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.BulkUploadResult;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.CreateEventDefinitionRequest;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinitionPage;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.UpdateEventDefinitionRequest;

public interface EventDefinitionService {

  Single<EventDefinition> createEventDefinition(CreateEventDefinitionRequest request);

  Completable updateEventDefinition(UpdateEventDefinitionRequest request);

  Single<EventDefinition> getEventDefinitionById(Long id);

  Single<EventDefinitionPage> queryEventDefinitions(
      String search, String category, int limit, int offset);

  Single<List<String>> getDistinctCategories();

  Completable archiveEventDefinition(Long id, String user);

  Single<BulkUploadResult> bulkUploadFromCsv(InputStream csvInputStream, String user);
}
