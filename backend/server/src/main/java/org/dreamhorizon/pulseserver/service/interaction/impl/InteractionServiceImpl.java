package org.dreamhorizon.pulseserver.service.interaction.impl;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.interaction.InteractionDao;
import org.dreamhorizon.pulseserver.dao.suggestedinteraction.SuggestedInteractionDao;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.InteractionFilterOptionsResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.TelemetryFilterOptionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.InteractionService;
import org.dreamhorizon.pulseserver.service.interaction.UploadInteractionDetailService;
import org.dreamhorizon.pulseserver.service.interaction.models.CreateInteractionRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.DeleteInteractionRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.Event;
import org.dreamhorizon.pulseserver.service.interaction.models.GetInteractionsRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.GetInteractionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.GetSuggestedInteractionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetailUploadMetadata;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetails;
import org.dreamhorizon.pulseserver.service.interaction.models.SuggestedInteractionDetails;
import org.dreamhorizon.pulseserver.service.interaction.models.UpdateInteractionRequest;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InteractionServiceImpl implements InteractionService {
  private final InteractionDao interactionDao;
  private final SuggestedInteractionDao suggestedInteractionDao;
  private final UploadInteractionDetailService uploadInteractionDetailService;

  private static final InteractionMapper mapper = InteractionMapper.INSTANCE;

  @Override
  public Single<InteractionDetails> createInteraction(@Valid CreateInteractionRequest request) {
    return validateInteractionAlreadyPresent(request)
        .flatMap(resp -> createInteractionInternal(request));
  }

  private Single<InteractionDetails> createInteractionInternal(CreateInteractionRequest request) {
    String projectId = ProjectContext.getProjectId();
    return interactionDao.createInteractionAndUploadMetadata(mapper.toInteractionDetails(request))
        .flatMap(resp -> Single.just(resp.getInteractionDetails()))
        .doOnSuccess(resp -> uploadInteractionDetailService
            .pushInteractionDetailsToObjectStore(projectId)
            .subscribe())
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
    String projectId = ProjectContext.getProjectId();
    return getInteractionDetails(request.getName())
        .flatMap(interaction -> this.patchInteraction(request, interaction))
        .flatMap(resp -> Single.just(EmptyResponse.emptyResponse))
        .doOnSuccess(resp -> uploadInteractionDetailService
            .pushInteractionDetailsToObjectStore(projectId)
            .subscribe())
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
    String projectId = ProjectContext.getProjectId();
    return interactionDao
        .deleteInteractionAndCreateUploadMetadata(deleteInteractionRequest)
        .map(res -> EmptyResponse.emptyResponse)
        .doOnSuccess(resp -> uploadInteractionDetailService
            .pushInteractionDetailsToObjectStore(projectId)
            .subscribe());
  }

  @Override
  public Single<List<InteractionDetails>> getInteractionConfig() {
    return interactionDao.getAllActiveAndRunningInteractions(ProjectContext.getProjectId());
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

  @Override
  public Single<GetSuggestedInteractionsResponse> getSuggestedInteractions() {
    String projectId = ProjectContext.getProjectId();
    return Single.zip(
        suggestedInteractionDao.getSuggestedInteractions(),
        interactionDao.getAllActiveAndRunningInteractions(projectId),
        (suggestionsResponse, existingInteractions) -> {
          List<SuggestedInteractionDetails> filtered = suggestionsResponse.getSuggestions().stream()
              .filter(s -> existingInteractions.stream()
                  .noneMatch(existing -> isSuggestionDuplicate(s, existing)))
              .toList();

          return GetSuggestedInteractionsResponse.builder()
              .suggestions(filtered)
              .totalSuggestions(filtered.size())
              .build();
        }
    ).doOnError(err -> log.error("error while getting suggested interactions", err));
  }

  @Override
  public Single<EmptyResponse> dismissSuggestion(Long suggestionId, String userEmail) {
    return suggestedInteractionDao.updateStatus(suggestionId, "DISMISSED", userEmail)
        .doOnError(err -> log.error("error while dismissing suggestion", err));
  }

  @Override
  public Single<EmptyResponse> activateSuggestion(Long suggestionId, String userEmail) {
    String projectId = ProjectContext.getProjectId();
    return suggestedInteractionDao.getSuggestionById(suggestionId)
        .flatMap(suggestion ->
            interactionDao.getAllActiveAndRunningInteractions(projectId)
                .flatMap(existingInteractions -> {
                  // Check for duplicate: same events in same order with same props
                  for (InteractionDetails existing : existingInteractions) {
                    if (isSuggestionDuplicate(suggestion, existing)) {
                      // Auto-dismiss the duplicate suggestion and return 400
                      return suggestedInteractionDao.updateStatus(suggestionId, "DISMISSED", userEmail)
                          .flatMap(dismissed -> Single.error(new WebApplicationException(
                              jakarta.ws.rs.core.Response.status(400)
                                  .entity(Map.of("error", Map.of(
                                      "code", "DUPLICATE_INTERACTION",
                                      "message", "An interaction with the same event sequence already exists ('"
                                          + existing.getName() + "')")))
                                  .type(MediaType.APPLICATION_JSON)
                                  .build())));
                    }
                  }
                  // No duplicate found — generate a unique name and create
                  String baseName = String.join(" -> ", suggestion.getPattern());
                  return generateUniqueName(baseName)
                      .flatMap(uniqueName -> {
                        CreateInteractionRequest request = buildCreateRequestFromSuggestion(suggestion, userEmail, uniqueName);
                        return createInteractionInternal(request)
                            .flatMap(created -> suggestedInteractionDao.updateStatus(suggestionId, "ACTIVATED", userEmail));
                      });
                })
        )
        .doOnError(err -> log.error("error while activating suggestion", err));
  }

  private Single<String> generateUniqueName(String baseName) {
    return interactionDao.isInteractionPresent(baseName)
        .flatMap(present -> {
          if (!present) {
            return Single.just(baseName);
          }
          return findAvailableName(baseName, 2);
        });
  }

  private Single<String> findAvailableName(String baseName, int suffix) {
    String candidate = baseName + " (" + suffix + ")";
    return interactionDao.isInteractionPresent(candidate)
        .flatMap(present -> {
          if (!present) {
            return Single.just(candidate);
          }
          return findAvailableName(baseName, suffix + 1);
        });
  }

  private CreateInteractionRequest buildCreateRequestFromSuggestion(
      SuggestedInteractionDetails suggestion, String userEmail, String name) {
    String description = String.format(
        "Auto-created from suggested interaction. Pattern: %s. Based on %d sessions (%.1f%% of traffic).",
        name, suggestion.getUniqueSessions(), suggestion.getSessionPct());

    List<Event> events = suggestion.getEvents();

    int lowerLimit = Math.max(1, (int) (suggestion.getMedianSpanS() * 1000));
    int midLimit = Math.max(lowerLimit + 1, (int) (suggestion.getMeanSpanS() * 1000));
    int upperLimit = Math.max(midLimit + 1, (int) (suggestion.getP95SpanS() * 1000));
    int threshold = Math.max(upperLimit + 1, (int) (suggestion.getP95SpanS() * 2 * 1000));

    return CreateInteractionRequest.builder()
        .name(name)
        .description(description)
        .events(events)
        .globalBlacklistedEvents(List.of())
        .uptimeLowerLimitInMs(lowerLimit)
        .uptimeMidLimitInMs(midLimit)
        .uptimeUpperLimitInMs(upperLimit)
        .thresholdInMs(threshold)
        .user(userEmail)
        .build();
  }

  private boolean isSuggestionDuplicate(SuggestedInteractionDetails suggestion, InteractionDetails existing) {
    List<Event> suggestedEvents = suggestion.getEvents();
    List<Event> existingEvents = existing.getEvents();

    if (suggestedEvents.size() != existingEvents.size()) {
      return false;
    }

    for (int i = 0; i < suggestedEvents.size(); i++) {
      if (!suggestedEvents.get(i).getName().equals(existingEvents.get(i).getName())) {
        return false;
      }
      if (!arePropsEqual(suggestedEvents.get(i).getProps(), existingEvents.get(i).getProps())) {
        return false;
      }
    }
    return true;
  }

  private boolean arePropsEqual(List<Event.Prop> props1, List<Event.Prop> props2) {
    List<Event.Prop> p1 = props1 != null ? props1 : List.of();
    List<Event.Prop> p2 = props2 != null ? props2 : List.of();

    if (p1.size() != p2.size()) {
      return false;
    }

    for (int i = 0; i < p1.size(); i++) {
      Event.Prop a = p1.get(i);
      Event.Prop b = p2.get(i);
      if (!Objects.equals(a.getName(), b.getName())
          || !Objects.equals(a.getValue(), b.getValue())
          || !Objects.equals(a.getOperator(), b.getOperator())) {
        return false;
      }
    }
    return true;
  }
}