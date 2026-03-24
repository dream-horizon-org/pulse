package org.dreamhorizon.pulseserver.dao.sessionreplay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.sessionreplay.models.BlockListingQueryRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.service.session.models.BlockListing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionReplayDaoTest {

  private static final String SESSION_ID = "sess-replay-123";
  private static final String PROJECT_ID = "proj-1";

  @Mock
  ClickhouseQueryService clickhouseQueryService;

  SessionReplayDao sessionReplayDao;

  @BeforeEach
  void setUp() {
    sessionReplayDao = new SessionReplayDao(clickhouseQueryService);
    ProjectContext.setProjectId(PROJECT_ID);
  }

  @AfterEach
  void tearDown() {
    ProjectContext.clear();
  }

  @Nested
  class QueryBlockListing {

    @Test
    void shouldThrowWhenProjectContextNotSet() {
      ProjectContext.clear();

      assertThatThrownBy(() -> sessionReplayDao.queryBlockListing(SESSION_ID))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No Project context");

      ProjectContext.setProjectId(PROJECT_ID);
    }

    @Test
    void shouldReturnEmptyListingWhenRowsAreNull() {
      QueryResultResponse<BlockListingQueryRow> response =
          QueryResultResponse.<BlockListingQueryRow>builder().rows(null).build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class),
          eq(BlockListingQueryRow.class)))
          .thenReturn(Single.just(response));

      BlockListing result = sessionReplayDao.queryBlockListing(SESSION_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getBlockUrls()).isEmpty();
      assertThat(result.getBlockFirstTimestamps()).isEmpty();
      assertThat(result.getBlockLastTimestamps()).isEmpty();
      assertThat(result.getSnapshotSource()).isNull();
    }

    @Test
    void shouldReturnEmptyListingWhenRowsAreEmpty() {
      QueryResultResponse<BlockListingQueryRow> response =
          QueryResultResponse.<BlockListingQueryRow>builder().rows(List.of()).build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class),
          eq(BlockListingQueryRow.class)))
          .thenReturn(Single.just(response));

      BlockListing result = sessionReplayDao.queryBlockListing(SESSION_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getBlockUrls()).isEmpty();
      assertThat(result.getBlockFirstTimestamps()).isEmpty();
      assertThat(result.getBlockLastTimestamps()).isEmpty();
      assertThat(result.getSnapshotSource()).isNull();
    }

    @Test
    void shouldMapRowToBlockListingWithParsedArrays() {
      BlockListingQueryRow row = new BlockListingQueryRow();
      row.setBlockFirstTimestamps("['2024-01-15T10:00:00Z','2024-01-15T10:01:00Z']");
      row.setBlockLastTimestamps("['2024-01-15T10:02:00Z','2024-01-15T10:03:00Z']");
      row.setBlockUrls("['s3://bucket/key1?range=bytes=0-100','s3://bucket/key2?range=bytes=101-200']");
      row.setSnapshotSource("mobile");

      QueryResultResponse<BlockListingQueryRow> response =
          QueryResultResponse.<BlockListingQueryRow>builder().rows(List.of(row)).build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class),
          eq(BlockListingQueryRow.class)))
          .thenReturn(Single.just(response));

      BlockListing result = sessionReplayDao.queryBlockListing(SESSION_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getBlockFirstTimestamps())
          .containsExactly("2024-01-15T10:00:00Z", "2024-01-15T10:01:00Z");
      assertThat(result.getBlockLastTimestamps())
          .containsExactly("2024-01-15T10:02:00Z", "2024-01-15T10:03:00Z");
      assertThat(result.getBlockUrls())
          .containsExactly(
              "s3://bucket/key1?range=bytes=0-100",
              "s3://bucket/key2?range=bytes=101-200");
      assertThat(result.getSnapshotSource()).isEqualTo("mobile");
    }

    @Test
    void shouldHandleNullArrayFields() {
      BlockListingQueryRow row = new BlockListingQueryRow();
      row.setBlockFirstTimestamps(null);
      row.setBlockLastTimestamps(null);
      row.setBlockUrls(null);
      row.setSnapshotSource("web");

      QueryResultResponse<BlockListingQueryRow> response =
          QueryResultResponse.<BlockListingQueryRow>builder().rows(List.of(row)).build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class),
          eq(BlockListingQueryRow.class)))
          .thenReturn(Single.just(response));

      BlockListing result = sessionReplayDao.queryBlockListing(SESSION_ID).blockingGet();

      assertThat(result.getBlockFirstTimestamps()).isEmpty();
      assertThat(result.getBlockLastTimestamps()).isEmpty();
      assertThat(result.getBlockUrls()).isEmpty();
      assertThat(result.getSnapshotSource()).isEqualTo("web");
    }

    @Test
    void shouldParseEmptyArrayStrings() {
      BlockListingQueryRow row = new BlockListingQueryRow();
      row.setBlockFirstTimestamps("[]");
      row.setBlockLastTimestamps("[]");
      row.setBlockUrls("[]");
      row.setSnapshotSource(null);

      QueryResultResponse<BlockListingQueryRow> response =
          QueryResultResponse.<BlockListingQueryRow>builder().rows(List.of(row)).build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class),
          eq(BlockListingQueryRow.class)))
          .thenReturn(Single.just(response));

      BlockListing result = sessionReplayDao.queryBlockListing(SESSION_ID).blockingGet();

      assertThat(result.getBlockFirstTimestamps()).isEmpty();
      assertThat(result.getBlockLastTimestamps()).isEmpty();
      assertThat(result.getBlockUrls()).isEmpty();
    }

    @Test
    void shouldSubstituteProjectIdAndSessionIdInQuery() {
      BlockListingQueryRow row = new BlockListingQueryRow();
      row.setBlockFirstTimestamps("[]");
      row.setBlockLastTimestamps("[]");
      row.setBlockUrls("[]");
      row.setSnapshotSource(null);

      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class),
          eq(BlockListingQueryRow.class)))
          .thenReturn(Single.just(
              QueryResultResponse.<BlockListingQueryRow>builder().rows(List.of(row)).build()));

      sessionReplayDao.queryBlockListing(SESSION_ID).blockingGet();

      ArgumentCaptor<QueryConfiguration> configCaptor = ArgumentCaptor.forClass(QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture(),
          eq(BlockListingQueryRow.class));
      String query = configCaptor.getValue().getQuery();
      assertThat(query).contains(PROJECT_ID);
      assertThat(query).contains(SESSION_ID);
      assertThat(configCaptor.getValue().getProjectId()).isEqualTo(PROJECT_ID);
      assertThat(configCaptor.getValue().getTimeoutMs()).isEqualTo(5000);
    }

    @Test
    void shouldPropagateErrorWhenClickHouseFails() {
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class),
          eq(BlockListingQueryRow.class)))
          .thenReturn(Single.error(new RuntimeException("ClickHouse connection failed")));

      sessionReplayDao.queryBlockListing(SESSION_ID)
          .test()
          .assertError(RuntimeException.class)
          .assertError(throwable -> throwable.getMessage().contains("ClickHouse"));
    }
  }
}
