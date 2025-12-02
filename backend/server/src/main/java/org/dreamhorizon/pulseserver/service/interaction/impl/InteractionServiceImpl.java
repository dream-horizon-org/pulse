package org.dreamhorizon.pulseserver.service.interaction.impl;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.interaction.InteractionDao;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.InteractionFilterOptionsResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.TelemetryFilterOptionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.InteractionService;
import org.dreamhorizon.pulseserver.service.interaction.models.CreateInteractionRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.DeleteInteractionRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.GetInteractionsRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.GetInteractionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetailUploadMetadata;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetails;
import org.dreamhorizon.pulseserver.service.interaction.models.UpdateInteractionRequest;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InteractionServiceImpl implements InteractionService {
  private final InteractionDao interactionDao;

  private static final InteractionMapper mapper = InteractionMapper.INSTANCE;

  @Override
  public Single<InteractionDetails> createInteraction(@Valid CreateInteractionRequest request) {
    return validateInteractionAlreadyPresent(request)
        .flatMap(resp -> interactionDao.createInteractionAndUploadMetadata(mapper.toInteractionDetails(request)))
        .flatMap(resp -> Single.just(resp.getInteractionDetails()))
        .doOnError(err -> log.error("error while creating interaction", err));
  }

  private @NotNull Single<EmptyResponse> validateInteractionAlreadyPresent(CreateInteractionRequest request) {
    return interactionDao
        .isInteractionPresent(request.getName())
        .flatMap(present -> {
          if (present) {
            return Single.error(new IllegalArgumentException("Interaction already exists"));
          }

          return Single.just(EmptyResponse.emptyResponse);
        });
  }

  @Override
  public Single<EmptyResponse> updateInteraction(@Valid UpdateInteractionRequest request) {
    return getInteractionDetails(request.getName())
        .flatMap(interaction -> this.patchInteraction(request, interaction))
        .flatMap(resp -> Single.just(EmptyResponse.emptyResponse))
        .doOnError(err -> log.error("error while updating interaction", err));
  }

  private Single<InteractionDetailUploadMetadata> patchInteraction(UpdateInteractionRequest request, InteractionDetails interaction) {
    InteractionDetails.InteractionDetailsBuilder updatedInteractionBuilder = interaction.toBuilder();

    if (Objects.nonNull(request.getDescription())) {
      updatedInteractionBuilder.description(request.getDescription());
    }

    if (Objects.nonNull(request.getUptimeLowerLimitInMs())) {
      updatedInteractionBuilder.uptimeLowerLimitInMs(request.getUptimeLowerLimitInMs());
    }

    if (Objects.nonNull(request.getUptimeMidLimitInMs())) {
      updatedInteractionBuilder.uptimeMidLimitInMs(request.getUptimeMidLimitInMs());
    }

    if (Objects.nonNull(request.getUptimeUpperLimitInMs())) {
      updatedInteractionBuilder.uptimeUpperLimitInMs(request.getUptimeUpperLimitInMs());
    }

    if (Objects.nonNull(request.getInteractionThresholdInMS())) {
      updatedInteractionBuilder.thresholdInMs(request.getInteractionThresholdInMS());
    }

    if (Objects.nonNull(request.getStatus())) {
      updatedInteractionBuilder.status(request.getStatus());
    }

    if (Objects.nonNull(request.getEvents())) {
      updatedInteractionBuilder.events(request.getEvents());
    }

    if (Objects.nonNull(request.getGlobalBlacklistedEvents())) {
      updatedInteractionBuilder.globalBlacklistedEvents(request.getGlobalBlacklistedEvents());
    }

    if (Objects.nonNull(request.getUser())) {
      updatedInteractionBuilder.updatedBy(request.getUser());
    }

    updatedInteractionBuilder.updatedAt(Timestamp.valueOf(LocalDateTime.now()));

    return interactionDao.updateInteractionAndCreateUploadMetadata(updatedInteractionBuilder.build());
  }

  @Override
  public Single<InteractionDetails> getInteractionDetails(String interactionName) {
    return interactionDao
        .getInteractionDetails(interactionName)
        .doOnError(err -> log.error("error while getting interaction details", err));
  }

  @Override
  public Single<GetInteractionsResponse> getInteractions(@Valid GetInteractionsRequest getInteractionsRequest) {
    return interactionDao
        .getInteractions(getInteractionsRequest)
        .doOnError(err -> log.error("error while getting interaction", err));
  }

  @Override
  public Single<EmptyResponse> deleteInteraction(DeleteInteractionRequest deleteInteractionRequest) {
    return interactionDao
        .deleteInteractionAndCreateUploadMetadata(deleteInteractionRequest)
        .map(res -> EmptyResponse.emptyResponse);
  }

  @Override
  public Single<List<InteractionDetails>> getAllActiveAndRunningInteractions() {
    return interactionDao.getAllActiveAndRunningInteractions();
  }

  @Override
  public Single<InteractionFilterOptionsResponse> getInteractionFilterOptions() {
    return interactionDao.getInteractionFilterOptions()
        .doOnError(err -> log.error("error while getting interaction filter options", err));
  }

  @Override
  public Single<TelemetryFilterOptionsResponse> getTelemetryFilterOptions() {
    return interactionDao.getTelemetryFilterOptions()
        .doOnError(err -> log.error("error while getting telemetry filter options", err));
  }
}