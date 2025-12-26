package org.dreamhorizon.pulseserver.service.interaction;

import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import java.util.List;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.InteractionFilterOptionsResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.TelemetryFilterOptionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.CreateInteractionRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.DeleteInteractionRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.GetInteractionsRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.GetInteractionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetails;
import org.dreamhorizon.pulseserver.service.interaction.models.UpdateInteractionRequest;

public interface InteractionService {
  Single<InteractionDetails> createInteraction(@Valid CreateInteractionRequest createInteractionRequest);

  Single<EmptyResponse> updateInteraction(@Valid UpdateInteractionRequest updateInteractionRequest);

  Single<InteractionDetails> getInteractionDetails(String interactionName);

  Single<GetInteractionsResponse> getInteractions(GetInteractionsRequest getInteractionsRequest);

  Single<EmptyResponse> deleteInteraction(DeleteInteractionRequest deleteInteractionRequest);

  Single<List<InteractionDetails>> getInteractionConfig();

  Single<InteractionFilterOptionsResponse> getInteractionFilterOptions();

  Single<TelemetryFilterOptionsResponse> getTelemetryFilterOptions();
}