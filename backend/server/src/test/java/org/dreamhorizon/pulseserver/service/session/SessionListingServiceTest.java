package org.dreamhorizon.pulseserver.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.resources.session.models.ImpactedInteractionsRow;
import org.dreamhorizon.pulseserver.resources.session.models.ImpactedScreensRow;
import org.dreamhorizon.pulseserver.resources.session.models.JourneyRow;
import org.dreamhorizon.pulseserver.resources.session.models.AdvancedFilterGroup;
import org.dreamhorizon.pulseserver.resources.session.models.FilterConditionRequest;
import org.dreamhorizon.pulseserver.resources.session.models.FiltersRequest;
import org.dreamhorizon.pulseserver.resources.session.models.PageRequest;
import org.dreamhorizon.pulseserver.resources.session.models.SessionListingRequest;
import org.dreamhorizon.pulseserver.resources.session.models.SessionListingResponse;
import org.dreamhorizon.pulseserver.resources.session.models.SessionRow;
import org.dreamhorizon.pulseserver.resources.session.models.TimeRangeRequest;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionListingServiceTest {

  private static final String PROJECT_ID = "project-1";

  @Mock
  ClickhouseQueryService clickhouseQueryService;

  SessionListingService sessionListingService;

  @BeforeEach
  void setUp() {
    sessionListingService = new SessionListingService(clickhouseQueryService);
    TenantContext.setTenantId("tenant-1");
    ProjectContext.setProjectId(PROJECT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    ProjectContext.clear();
  }

  private static SessionListingRequest minimalRequest() {
    return SessionListingRequest.builder()
        .timeRange(TimeRangeRequest.builder()
            .from("2024-01-01T00:00:00Z")
            .to("2024-01-01T23:59:59Z")
            .build())
        .build();
  }

  @Nested
  class Validation {

    @Test
    void shouldThrowWhenTimeRangeIsNull() {
      SessionListingRequest request = new SessionListingRequest();

      assertThatThrownBy(() -> sessionListingService.getSessionListing(request).blockingGet())
          .hasMessageContaining("Missing required field: timeRange");
    }

    @Test
    void shouldThrowWhenTimeRangeFromIsBlank() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder()
              .from("")
              .to("2024-01-01T23:59:59Z")
              .build())
          .build();

      assertThatThrownBy(() -> sessionListingService.getSessionListing(request).blockingGet())
          .hasMessageContaining("timeRange.from");
    }

    @Test
    void shouldThrowWhenTimeRangeToIsBlank() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder()
              .from("2024-01-01T00:00:00Z")
              .to(" ")
              .build())
          .build();

      assertThatThrownBy(() -> sessionListingService.getSessionListing(request).blockingGet())
          .hasMessageContaining("timeRange.to");
    }
  }

  @Nested
  class GetSessionListing {

    @Test
    void shouldReturnEmptyPageWhenNoRows() {
      SessionListingRequest request = minimalRequest();

      QueryResultResponse<SessionRow> emptyListing =
          QueryResultResponse.<SessionRow>builder()
              .rows(Collections.emptyList())
              .jobComplete(true)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(emptyListing));

      SessionListingResponse result = sessionListingService.getSessionListing(request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getSessions()).isEmpty();
      assertThat(result.getPage().isHasMore()).isFalse();
      assertThat(result.getPage().getLimit()).isEqualTo(10);
      assertThat(result.getPage().getNextCursor()).isNull();
    }

    @Test
    void shouldReturnSessionsWithJourneyScreensAndImpactedInteractions() {
      SessionListingRequest request = minimalRequest();

      SessionRow row1 = SessionRow.builder()
          .sessionId("s1")
          .startTime("2024-01-01T12:00:00Z")
          .durationMs(5000L)
          .user("u1")
          .qualityScore(0.9)
          .platform("Android")
          .spanCount(100L)
          .crashCount(0L)
          .anrCount(0L)
          .networkErrors(0L)
          .nonFatal(0L)
          .interactionErrors(0L)
          .slowInteractionCount(0L)
          .frozenFrameCount(null)
          .build();

      QueryResultResponse<SessionRow> listingResponse =
          QueryResultResponse.<SessionRow>builder()
              .rows(List.of(row1))
              .jobComplete(true)
              .build();

      JourneyRow journeyRow = JourneyRow.builder()
          .sessionId("s1")
          .journey("ScreenA|||ScreenB|||ScreenC")
          .build();
      QueryResultResponse<JourneyRow> journeyResponse =
          QueryResultResponse.<JourneyRow>builder()
              .rows(List.of(journeyRow))
              .jobComplete(true)
              .build();

      ImpactedScreensRow screensRow = ImpactedScreensRow.builder()
          .sessionId("s1")
          .crashScreens("CrashScreen")
          .anrScreens("")
          .nonFatalScreens("NonFatal1|||NonFatal2")
          .build();
      QueryResultResponse<ImpactedScreensRow> screensResponse =
          QueryResultResponse.<ImpactedScreensRow>builder()
              .rows(List.of(screensRow))
              .jobComplete(true)
              .build();

      ImpactedInteractionsRow interactionsRow = ImpactedInteractionsRow.builder()
          .sessionId("s1")
          .impactedInteractionNames("ButtonTap|||Scroll")
          .build();
      QueryResultResponse<ImpactedInteractionsRow> interactionsResponse =
          QueryResultResponse.<ImpactedInteractionsRow>builder()
              .rows(List.of(interactionsRow))
              .jobComplete(true)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(listingResponse));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(JourneyRow.class)))
          .thenReturn(Single.just(journeyResponse));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(ImpactedScreensRow.class)))
          .thenReturn(Single.just(screensResponse));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(ImpactedInteractionsRow.class)))
          .thenReturn(Single.just(interactionsResponse));

      SessionListingResponse result = sessionListingService.getSessionListing(request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getSessions()).hasSize(1);
      SessionListingResponse.SessionItem item = result.getSessions().get(0);
      assertThat(item.getSessionId()).isEqualTo("s1");
      assertThat(item.getJourney()).containsExactly("ScreenA", "ScreenB", "ScreenC");
      assertThat(item.getImpactedScreens()).containsKeys("crashes", "nonFatals");
      assertThat(item.getImpactedScreens().get("crashes")).containsExactly("CrashScreen");
      assertThat(item.getImpactedScreens().get("nonFatals")).containsExactly("NonFatal1", "NonFatal2");
      assertThat(item.getImpactedInteractions()).containsExactly("ButtonTap", "Scroll");
      assertThat(result.getPage().isHasMore()).isFalse();
      assertThat(result.getPage().getNextCursor()).isNull();
    }

    @Test
    void shouldUseDefaultPageSizeWhenPageNotSpecified() {
      SessionListingRequest request = minimalRequest();
      QueryResultResponse<SessionRow> empty =
          QueryResultResponse.<SessionRow>builder().rows(Collections.emptyList()).jobComplete(true).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(empty));

      SessionListingResponse result = sessionListingService.getSessionListing(request).blockingGet();

      assertThat(result.getPage().getLimit()).isEqualTo(10);
    }

    @Test
    void shouldPropagateDatabaseError() {
      SessionListingRequest request = minimalRequest();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.error(new RuntimeException("ClickHouse connection failed")));

      assertThatThrownBy(() -> sessionListingService.getSessionListing(request).blockingGet())
          .isInstanceOf(jakarta.ws.rs.WebApplicationException.class)
          .hasMessageContaining("ClickHouse connection failed");
    }

    @Test
    void shouldReturnNextCursorWhenHasMore() {
      SessionListingRequest request = minimalRequest();
      // Default pageSize=10; return 11 rows so hasMore is true and nextCursor is set
      List<SessionRow> elevenRows = new java.util.ArrayList<>();
      for (int i = 1; i <= 11; i++) {
        elevenRows.add(SessionRow.builder()
            .sessionId("s" + i)
            .startTime("2024-01-01T12:00:00Z")
            .durationMs(5000L)
            .user("u" + i)
            .qualityScore(0.9)
            .platform("Android")
            .spanCount(100L)
            .crashCount(0L)
            .anrCount(0L)
            .networkErrors(0L)
            .nonFatal(0L)
            .interactionErrors(0L)
            .slowInteractionCount(0L)
            .frozenFrameCount(null)
            .build());
      }
      QueryResultResponse<SessionRow> listingResponse =
          QueryResultResponse.<SessionRow>builder()
              .rows(elevenRows)
              .jobComplete(true)
              .build();
      List<JourneyRow> journeyRows = new java.util.ArrayList<>();
      for (int i = 1; i <= 10; i++) {
        journeyRows.add(JourneyRow.builder().sessionId("s" + i).journey("A").build());
      }
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(listingResponse));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(JourneyRow.class)))
          .thenReturn(Single.just(QueryResultResponse.<JourneyRow>builder()
              .rows(journeyRows).jobComplete(true).build()));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(ImpactedScreensRow.class)))
          .thenReturn(Single.just(QueryResultResponse.<ImpactedScreensRow>builder()
              .rows(Collections.emptyList()).jobComplete(true).build()));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(ImpactedInteractionsRow.class)))
          .thenReturn(Single.just(QueryResultResponse.<ImpactedInteractionsRow>builder()
              .rows(Collections.emptyList()).jobComplete(true).build()));

      SessionListingResponse result = sessionListingService.getSessionListing(request).blockingGet();

      assertThat(result.getSessions()).hasSize(10);
      assertThat(result.getPage().isHasMore()).isTrue();
      assertThat(result.getPage().getNextCursor()).isNotNull();
      assertThat(result.getPage().getLimit()).isEqualTo(10);
    }

    @Test
    void shouldReturnEmptyJourneyAndImpactedInteractionsWhenQueriesReturnEmpty() {
      SessionListingRequest request = minimalRequest();
      SessionRow row = SessionRow.builder()
          .sessionId("s1")
          .startTime("2024-01-01T12:00:00Z")
          .durationMs(5000L)
          .user("u1")
          .qualityScore(0.9)
          .platform("Android")
          .spanCount(100L)
          .crashCount(0L)
          .anrCount(0L)
          .networkErrors(0L)
          .nonFatal(0L)
          .interactionErrors(0L)
          .slowInteractionCount(0L)
          .frozenFrameCount(null)
          .build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(QueryResultResponse.<SessionRow>builder()
              .rows(List.of(row)).jobComplete(true).build()));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(JourneyRow.class)))
          .thenReturn(Single.just(QueryResultResponse.<JourneyRow>builder()
              .rows(Collections.emptyList()).jobComplete(true).build()));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(ImpactedScreensRow.class)))
          .thenReturn(Single.just(QueryResultResponse.<ImpactedScreensRow>builder()
              .rows(Collections.emptyList()).jobComplete(true).build()));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(ImpactedInteractionsRow.class)))
          .thenReturn(Single.just(QueryResultResponse.<ImpactedInteractionsRow>builder()
              .rows(Collections.emptyList()).jobComplete(true).build()));

      SessionListingResponse result = sessionListingService.getSessionListing(request).blockingGet();

      assertThat(result.getSessions()).hasSize(1);
      assertThat(result.getSessions().get(0).getJourney()).isEmpty();
      assertThat(result.getSessions().get(0).getImpactedScreens()).isNull();
      assertThat(result.getSessions().get(0).getImpactedInteractions()).isEmpty();
    }

    @Test
    void shouldApplyPageLimit() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder().from("2024-01-01T00:00:00Z").to("2024-01-01T23:59:59Z").build())
          .page(PageRequest.builder().limit(25).build())
          .build();
      QueryResultResponse<SessionRow> empty =
          QueryResultResponse.<SessionRow>builder().rows(Collections.emptyList()).jobComplete(true).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(empty));

      SessionListingResponse result = sessionListingService.getSessionListing(request).blockingGet();

      assertThat(result.getPage().getLimit()).isEqualTo(25);
    }

    @Test
    void shouldUseDefaultPageSizeWhenLimitZero() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder().from("2024-01-01T00:00:00Z").to("2024-01-01T23:59:59Z").build())
          .page(PageRequest.builder().limit(0).build())
          .build();
      QueryResultResponse<SessionRow> empty =
          QueryResultResponse.<SessionRow>builder().rows(Collections.emptyList()).jobComplete(true).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(empty));

      SessionListingResponse result = sessionListingService.getSessionListing(request).blockingGet();

      assertThat(result.getPage().getLimit()).isEqualTo(10);
    }

    @Test
    void shouldCapPageLimitAtMax() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder().from("2024-01-01T00:00:00Z").to("2024-01-01T23:59:59Z").build())
          .page(PageRequest.builder().limit(150).build())
          .build();
      QueryResultResponse<SessionRow> empty =
          QueryResultResponse.<SessionRow>builder().rows(Collections.emptyList()).jobComplete(true).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(empty));

      SessionListingResponse result = sessionListingService.getSessionListing(request).blockingGet();

      assertThat(result.getPage().getLimit()).isEqualTo(100);
    }

    @Test
    void shouldHandleNullRowsInQueryResult() {
      SessionListingRequest request = minimalRequest();
      QueryResultResponse<SessionRow> listingWithNullRows =
          QueryResultResponse.<SessionRow>builder().rows(null).jobComplete(true).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(listingWithNullRows));

      SessionListingResponse result = sessionListingService.getSessionListing(request).blockingGet();

      assertThat(result.getSessions()).isEmpty();
      assertThat(result.getPage().isHasMore()).isFalse();
    }

    @Test
    void shouldReturnSessionWithIssuesAndAnrScreensOnly() {
      SessionListingRequest request = minimalRequest();
      SessionRow row = SessionRow.builder()
          .sessionId("s1")
          .startTime("2024-01-01T12:00:00Z")
          .durationMs(5000L)
          .user("u1")
          .qualityScore(0.5)
          .platform("iOS")
          .spanCount(50L)
          .crashCount(1L)
          .anrCount(1L)
          .networkErrors(2L)
          .nonFatal(1L)
          .interactionErrors(1L)
          .slowInteractionCount(1L)
          .frozenFrameCount(1.5)
          .build();
      ImpactedScreensRow screensRow = ImpactedScreensRow.builder()
          .sessionId("s1")
          .crashScreens("")
          .anrScreens("AnrScreen1")
          .nonFatalScreens("")
          .build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(QueryResultResponse.<SessionRow>builder().rows(List.of(row)).jobComplete(true).build()));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(JourneyRow.class)))
          .thenReturn(Single.just(QueryResultResponse.<JourneyRow>builder()
              .rows(List.of(JourneyRow.builder().sessionId("s1").journey("").build())).jobComplete(true).build()));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(ImpactedScreensRow.class)))
          .thenReturn(Single.just(QueryResultResponse.<ImpactedScreensRow>builder()
              .rows(List.of(screensRow)).jobComplete(true).build()));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(ImpactedInteractionsRow.class)))
          .thenReturn(Single.just(QueryResultResponse.<ImpactedInteractionsRow>builder()
              .rows(Collections.emptyList()).jobComplete(true).build()));

      SessionListingResponse result = sessionListingService.getSessionListing(request).blockingGet();

      assertThat(result.getSessions()).hasSize(1);
      SessionListingResponse.SessionItem item = result.getSessions().get(0);
      assertThat(item.getIssues()).isNotEmpty();
      assertThat(item.getIssues()).extracting("type").contains("CRASH", "ANR", "NETWORK_ERROR", "NON_FATAL", "INTERACTION_ERROR", "SLOW_INTERACTION", "FROZEN_FRAME");
      assertThat(item.getImpactedScreens()).containsOnlyKeys("anrs");
      assertThat(item.getImpactedScreens().get("anrs")).containsExactly("AnrScreen1");
      assertThat(item.getJourney()).isEmpty();
    }

    @Test
    void shouldApplyAdvancedFilterWithOrOp() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder().from("2024-01-01T00:00:00Z").to("2024-01-01T23:59:59Z").build())
          .filters(FiltersRequest.builder()
              .advanced(AdvancedFilterGroup.builder()
                  .op("OR")
                  .children(List.of(FilterConditionRequest.builder()
                      .field("PLATFORM")
                      .operator("EQ")
                      .value("Android")
                      .build()))
                  .build())
              .build())
          .build();
      QueryResultResponse<SessionRow> empty =
          QueryResultResponse.<SessionRow>builder().rows(Collections.emptyList()).jobComplete(true).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(empty));

      SessionListingResponse result = sessionListingService.getSessionListing(request).blockingGet();

      assertThat(result.getSessions()).isEmpty();
      verify(clickhouseQueryService).executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class));
    }

    @Test
    void shouldApplySortByAndSortDirection() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder().from("2024-01-01T00:00:00Z").to("2024-01-01T23:59:59Z").build())
          .sortBy("DURATION")
          .sortDirection("ASC")
          .build();
      QueryResultResponse<SessionRow> empty =
          QueryResultResponse.<SessionRow>builder().rows(Collections.emptyList()).jobComplete(true).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(empty));

      SessionListingResponse result = sessionListingService.getSessionListing(request).blockingGet();

      assertThat(result.getSessions()).isEmpty();
      verify(clickhouseQueryService).executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class));
    }
  }

  @Nested
  class Filters {

    @Test
    void shouldApplyQuickFilter() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder().from("2024-01-01T00:00:00Z").to("2024-01-01T23:59:59Z").build())
          .filters(FiltersRequest.builder().quick(List.of("SLOW")).build())
          .build();
      QueryResultResponse<SessionRow> empty =
          QueryResultResponse.<SessionRow>builder().rows(Collections.emptyList()).jobComplete(true).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(empty));

      SessionListingResponse result = sessionListingService.getSessionListing(request).blockingGet();

      assertThat(result.getSessions()).isEmpty();
      verify(clickhouseQueryService).executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class));
    }

    @Test
    void shouldThrowOnInvalidQuickFilter() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder().from("2024-01-01T00:00:00Z").to("2024-01-01T23:59:59Z").build())
          .filters(FiltersRequest.builder().quick(List.of("INVALID_QUICK")).build())
          .build();

      assertThatThrownBy(() -> sessionListingService.getSessionListing(request).blockingGet())
          .hasMessageContaining("Unknown quick filter");
    }

    @Test
    void shouldThrowOnInvalidFilterOp() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder().from("2024-01-01T00:00:00Z").to("2024-01-01T23:59:59Z").build())
          .filters(FiltersRequest.builder()
              .advanced(AdvancedFilterGroup.builder().op("XOR").children(Collections.emptyList()).build())
              .build())
          .build();

      assertThatThrownBy(() -> sessionListingService.getSessionListing(request).blockingGet())
          .hasMessageContaining("Invalid filter op");
    }

    @Test
    void shouldThrowWhenFilterConditionMissingField() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder().from("2024-01-01T00:00:00Z").to("2024-01-01T23:59:59Z").build())
          .filters(FiltersRequest.builder()
              .advanced(AdvancedFilterGroup.builder()
                  .op("AND")
                  .children(List.of(FilterConditionRequest.builder()
                      .field("")
                      .operator("EQ")
                      .value("x")
                      .build()))
                  .build())
              .build())
          .build();

      assertThatThrownBy(() -> sessionListingService.getSessionListing(request).blockingGet())
          .hasMessageContaining("Filter condition missing 'field'");
    }

    @Test
    void shouldThrowWhenFilterConditionMissingOperator() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder().from("2024-01-01T00:00:00Z").to("2024-01-01T23:59:59Z").build())
          .filters(FiltersRequest.builder()
              .advanced(AdvancedFilterGroup.builder()
                  .op("AND")
                  .children(List.of(FilterConditionRequest.builder()
                      .field("PLATFORM")
                      .operator(null)
                      .value("Android")
                      .build()))
                  .build())
              .build())
          .build();

      assertThatThrownBy(() -> sessionListingService.getSessionListing(request).blockingGet())
          .hasMessageContaining("Filter condition missing 'operator'");
    }
  }

  @Nested
  class SortAndSearch {

    @Test
    void shouldThrowOnInvalidSortBy() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder().from("2024-01-01T00:00:00Z").to("2024-01-01T23:59:59Z").build())
          .sortBy("INVALID_SORT_FIELD")
          .build();

      assertThatThrownBy(() -> sessionListingService.getSessionListing(request).blockingGet())
          .hasMessageContaining("Unknown sortBy");
    }

    @Test
    void shouldApplySearchQuery() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder().from("2024-01-01T00:00:00Z").to("2024-01-01T23:59:59Z").build())
          .query("  user-42  ")
          .build();
      QueryResultResponse<SessionRow> empty =
          QueryResultResponse.<SessionRow>builder().rows(Collections.emptyList()).jobComplete(true).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class)))
          .thenReturn(Single.just(empty));

      sessionListingService.getSessionListing(request).blockingGet();

      verify(clickhouseQueryService).executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionRow.class));
    }
  }

  @Nested
  class Cursor {

    @Test
    void shouldThrowOnInvalidCursor() {
      SessionListingRequest request = SessionListingRequest.builder()
          .timeRange(TimeRangeRequest.builder().from("2024-01-01T00:00:00Z").to("2024-01-01T23:59:59Z").build())
          .page(PageRequest.builder().cursor("not-valid-base64-json").build())
          .build();

      assertThatThrownBy(() -> sessionListingService.getSessionListing(request).blockingGet())
          .hasMessageContaining("Invalid cursor");
    }
  }
}
