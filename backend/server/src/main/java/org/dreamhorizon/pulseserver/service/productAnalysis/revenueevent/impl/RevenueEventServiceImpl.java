package org.dreamhorizon.pulseserver.service.productAnalysis.revenueevent.impl;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dreamhorizon.pulseserver.dao.productAnalysis.revenueevent.RevenueEventDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.revenueevent.models.RevenueEventRow;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.CreateRevenueEventRequest;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.RevenueEventListResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.RevenueEventResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.UpdateRevenueEventRequest;
import org.dreamhorizon.pulseserver.service.productAnalysis.revenueevent.RevenueEventService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RevenueEventServiceImpl implements RevenueEventService {

  private final RevenueEventDao revenueEventDao;

  @Override
  public Single<RevenueEventListResponse> list(String projectId) {
    return revenueEventDao
      .listByProject(projectId)
      .map(
        rows ->
          RevenueEventListResponse.builder()
            .revenueEvents(rows.stream().map(this::toResponse).collect(Collectors.toList()))
            .build());
  }

  @Override
  public Single<RevenueEventResponse> create(
    String projectId, CreateRevenueEventRequest request, String configuredBy) {
    validateCurrencyConfig(request.getCurrency(), request.getCurrencyAttribute());

    String eventName = request.getEventName().trim();
    String valueAttribute = request.getValueAttribute().trim();
    String currency = normalizeCurrency(request.getCurrency());
    String currencyAttribute = StringUtils.trimToNull(request.getCurrencyAttribute());

    return revenueEventDao
      .findByProjectAndEventName(projectId, eventName)
      .flatMapSingle(
        existing ->
          Single.<RevenueEventResponse>error(
            ServiceError.REVENUE_EVENT_ALREADY_EXISTS.getCustomException(
              "A revenue event is already configured for \"" + eventName + "\"")))
      .switchIfEmpty(
        Single.defer(
          () -> {
            String id = UUID.randomUUID().toString();
            RevenueEventRow row =
              RevenueEventRow.builder()
                .id(id)
                .projectId(projectId)
                .eventName(eventName)
                .valueAttribute(valueAttribute)
                .currency(currency)
                .currencyAttribute(currencyAttribute)
                .conversionWindowHours(request.getConversionWindowHours())
                .configuredBy(configuredBy)
                .build();
            return revenueEventDao
              .insert(row)
              .andThen(
                revenueEventDao
                  .findByProjectAndId(projectId, id)
                  .map(this::toResponse)
                  .switchIfEmpty(
                    Single.error(
                      ServiceError.REVENUE_EVENT_CREATION_FAILED.getCustomException(
                        "Failed to load revenue event after create"))));
          }));
  }

  @Override
  public Completable update(
    String projectId, String id, UpdateRevenueEventRequest request, String configuredBy) {
    validateCurrencyConfig(request.getCurrency(), request.getCurrencyAttribute());

    String eventName = request.getEventName().trim();
    String valueAttribute = request.getValueAttribute().trim();
    String currency = normalizeCurrency(request.getCurrency());
    String currencyAttribute = StringUtils.trimToNull(request.getCurrencyAttribute());

    return revenueEventDao
      .findByProjectAndId(projectId, id)
      .switchIfEmpty(
        Maybe.error(ServiceError.REVENUE_EVENT_NOT_FOUND.getException()))
      .flatMapCompletable(
        existing ->
          revenueEventDao
            .findByProjectAndEventName(projectId, eventName)
            .flatMapCompletable(
              duplicate -> {
                if (!duplicate.getId().equals(id)) {
                  return Completable.error(
                    ServiceError.REVENUE_EVENT_ALREADY_EXISTS.getCustomException(
                      "A revenue event is already configured for \"" + eventName + "\""));
                }
                return Completable.complete();
              })
            .andThen(
              Completable.defer(
                () -> {
                  RevenueEventRow row =
                    RevenueEventRow.builder()
                      .eventName(eventName)
                      .valueAttribute(valueAttribute)
                      .currency(currency)
                      .currencyAttribute(currencyAttribute)
                      .conversionWindowHours(request.getConversionWindowHours())
                      .configuredBy(configuredBy)
                      .build();
                  return revenueEventDao
                    .update(projectId, id, row)
                    .flatMapCompletable(
                      updated -> {
                        if (updated == 0) {
                          return Completable.error(
                            ServiceError.REVENUE_EVENT_NOT_FOUND.getException());
                        }
                        return Completable.complete();
                      });
                })));
  }

  @Override
  public Completable delete(String projectId, String id) {
    return revenueEventDao
      .delete(projectId, id)
      .flatMapCompletable(
        deleted -> {
          if (deleted == 0) {
            return Completable.error(ServiceError.REVENUE_EVENT_NOT_FOUND.getException());
          }
          return Completable.complete();
        });
  }

  private RevenueEventResponse toResponse(RevenueEventRow row) {
    return RevenueEventResponse.builder()
      .id(row.getId())
      .eventName(row.getEventName())
      .valueAttribute(row.getValueAttribute())
      .currency(StringUtils.defaultString(row.getCurrency()))
      .currencyAttribute(row.getCurrencyAttribute())
      .conversionWindowHours(row.getConversionWindowHours())
      .configuredBy(row.getConfiguredBy())
      .configuredAt(row.getConfiguredAt())
      .build();
  }

  private static void validateCurrencyConfig(String currency, String currencyAttribute) {
    boolean hasFixed = StringUtils.isNotBlank(currency);
    boolean hasAttribute = StringUtils.isNotBlank(currencyAttribute);
    if (!hasFixed && !hasAttribute) {
      throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
        "Either currency or currencyAttribute is required");
    }
  }

  private static String normalizeCurrency(String currency) {
    return currency == null ? "" : currency.trim();
  }
}
