package org.dreamhorizon.pulseserver.service.productAnalysis.revenueevent.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.productAnalysis.revenueevent.RevenueEventDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.revenueevent.models.RevenueEventRow;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.CreateRevenueEventRequest;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.UpdateRevenueEventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevenueEventServiceImplTest {

  private static final String PROJECT = "proj-1";
  private static final String ID = "550e8400-e29b-41d4-a716-446655440000";

  @Mock
  RevenueEventDao revenueEventDao;

  RevenueEventServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new RevenueEventServiceImpl(revenueEventDao);
  }

  private RevenueEventRow storedRow() {
    return RevenueEventRow.builder()
      .id(ID)
      .projectId(PROJECT)
      .eventName("order_placed")
      .valueAttribute("order_amount")
      .currency("INR")
      .currencyAttribute(null)
      .conversionWindowHours(24)
      .configuredBy("pm@example.com")
      .configuredAt(Instant.parse("2026-01-15T10:00:00Z"))
      .build();
  }

  @Nested
  class ListRevenueEvents {

    @Test
    void shouldMapRowsToResponse() {
      when(revenueEventDao.listByProject(PROJECT))
        .thenReturn(Single.just(Collections.singletonList(storedRow())));

      service
        .list(PROJECT)
        .test()
        .assertValue(
          resp -> {
            assertThat(resp.getRevenueEvents()).hasSize(1);
            assertThat(resp.getRevenueEvents().get(0).getEventName()).isEqualTo("order_placed");
            assertThat(resp.getRevenueEvents().get(0).getConfiguredAt()).isNotNull();
            return true;
          });
    }
  }

  @Nested
  class Create {

    @Test
    void shouldCreateRevenueEventWhenEventNameIsUnique() {
      when(revenueEventDao.findByProjectAndEventName(PROJECT, "order_placed"))
        .thenReturn(Maybe.empty());
      when(revenueEventDao.insert(any(RevenueEventRow.class))).thenReturn(Completable.complete());
      when(revenueEventDao.findByProjectAndId(org.mockito.ArgumentMatchers.eq(PROJECT), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Maybe.just(storedRow()));

      CreateRevenueEventRequest request =
        CreateRevenueEventRequest.builder()
          .eventName("order_placed")
          .valueAttribute("order_amount")
          .currency("INR")
          .conversionWindowHours(24)
          .build();

      service
        .create(PROJECT, request, "pm@example.com")
        .test()
        .assertValue(resp -> resp.getEventName().equals("order_placed"));

      ArgumentCaptor<RevenueEventRow> captor = ArgumentCaptor.forClass(RevenueEventRow.class);
      verify(revenueEventDao).insert(captor.capture());
      assertThat(captor.getValue().getProjectId()).isEqualTo(PROJECT);
      assertThat(captor.getValue().getConfiguredBy()).isEqualTo("pm@example.com");
    }

    @Test
    void shouldRejectDuplicateEventName() {
      when(revenueEventDao.findByProjectAndEventName(PROJECT, "order_placed"))
        .thenReturn(Maybe.just(storedRow()));

      CreateRevenueEventRequest request =
        CreateRevenueEventRequest.builder()
          .eventName("order_placed")
          .valueAttribute("order_amount")
          .currency("INR")
          .conversionWindowHours(24)
          .build();

      service.create(PROJECT, request, "pm@example.com").test().assertError(
        err -> err.getMessage().contains("already configured"));
    }
  }

  @Nested
  class Update {

    @Test
    void shouldUpdateExistingConfig() {
      when(revenueEventDao.findByProjectAndId(PROJECT, ID)).thenReturn(Maybe.just(storedRow()));
      when(revenueEventDao.findByProjectAndEventName(PROJECT, "order_placed"))
        .thenReturn(Maybe.just(storedRow()));
      when(revenueEventDao.update(eq(PROJECT), eq(ID), any(RevenueEventRow.class)))
        .thenReturn(Single.just(1));

      UpdateRevenueEventRequest request =
        UpdateRevenueEventRequest.builder()
          .eventName("order_placed")
          .valueAttribute("total_amount")
          .currency("USD")
          .conversionWindowHours(48)
          .build();

      service.update(PROJECT, ID, request, "editor@example.com").test().assertComplete();
    }

    @Test
    void shouldFailWhenConfigMissing() {
      when(revenueEventDao.findByProjectAndId(PROJECT, ID)).thenReturn(Maybe.empty());

      UpdateRevenueEventRequest request =
        UpdateRevenueEventRequest.builder()
          .eventName("order_placed")
          .valueAttribute("order_amount")
          .currency("INR")
          .conversionWindowHours(24)
          .build();

      service.update(PROJECT, ID, request, "editor@example.com").test().assertError(
        err -> err.getMessage().contains("not found"));
    }
  }

  @Nested
  class Delete {

    @Test
    void shouldDeleteExistingConfig() {
      when(revenueEventDao.delete(PROJECT, ID)).thenReturn(Single.just(1));

      service.delete(PROJECT, ID).test().assertComplete();
    }

    @Test
    void shouldFailWhenConfigMissing() {
      when(revenueEventDao.delete(PROJECT, ID)).thenReturn(Single.just(0));

      service.delete(PROJECT, ID).test().assertError(
        err -> err.getMessage().contains("not found"));
    }
  }
}
