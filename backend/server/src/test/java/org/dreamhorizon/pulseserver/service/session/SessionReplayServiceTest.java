package org.dreamhorizon.pulseserver.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.sessionreplay.SessionReplayDao;
import org.dreamhorizon.pulseserver.resources.session.models.BlockSource;
import org.dreamhorizon.pulseserver.resources.session.models.SnapshotSourcesResponse;
import org.dreamhorizon.pulseserver.service.session.models.BlockListing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionReplayServiceTest {

  private static final String SESSION_ID = "sess-replay-123";

  @Mock
  SessionReplayDao sessionReplayDao;

  @Mock
  SessionBlockFetcher sessionBlockFetcher;

  SessionReplayService sessionReplayService;

  @BeforeEach
  void setUp() {
    sessionReplayService = new SessionReplayService(sessionReplayDao, sessionBlockFetcher);
  }

  @Nested
  class GetBlockSources {

    @Test
    void shouldReturnSourcesResponseWithSortedBlocks() {
      BlockListing listing = BlockListing.builder()
          .blockFirstTimestamps(List.of("2024-01-15T10:01:00Z", "2024-01-15T10:00:00Z"))
          .blockLastTimestamps(List.of("2024-01-15T10:02:00Z", "2024-01-15T10:01:30Z"))
          .blockUrls(List.of(
              "s3://bucket/key2?range=bytes=100-200",
              "s3://bucket/key1?range=bytes=0-99"))
          .snapshotSource("mobile")
          .build();

      when(sessionReplayDao.queryBlockListing(eq(SESSION_ID)))
          .thenReturn(Single.just(listing));

      SnapshotSourcesResponse result = sessionReplayService.getBlockSources(SESSION_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
      assertThat(result.getSnapshotSource()).isEqualTo("mobile");
      assertThat(result.getSources()).hasSize(2);
      // Blocks sorted by firstTimestamp: 10:00:00 then 10:01:00
      assertThat(result.getSources().get(0).getBlobKey()).isEqualTo("0");
      assertThat(result.getSources().get(0).getStartTimestamp()).isEqualTo("2024-01-15T10:00:00Z");
      assertThat(result.getSources().get(0).getEndTimestamp()).isEqualTo("2024-01-15T10:01:30Z");
      assertThat(result.getSources().get(0).getSource()).isEqualTo("blob");
      assertThat(result.getSources().get(1).getBlobKey()).isEqualTo("1");
      assertThat(result.getSources().get(1).getStartTimestamp()).isEqualTo("2024-01-15T10:01:00Z");
      verify(sessionReplayDao).queryBlockListing(SESSION_ID);
    }

    @Test
    void shouldReturnEmptySourcesWhenListingHasNoBlocks() {
      BlockListing listing = BlockListing.builder()
          .blockFirstTimestamps(List.of())
          .blockLastTimestamps(List.of())
          .blockUrls(List.of())
          .snapshotSource(null)
          .build();

      when(sessionReplayDao.queryBlockListing(eq(SESSION_ID)))
          .thenReturn(Single.just(listing));

      SnapshotSourcesResponse result = sessionReplayService.getBlockSources(SESSION_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
      assertThat(result.getSources()).isEmpty();
      assertThat(result.getSnapshotSource()).isNull();
    }

    @Test
    void shouldReturnEmptySourcesWhenListingHasNullBlockUrls() {
      BlockListing listing = BlockListing.builder()
          .blockFirstTimestamps(null)
          .blockLastTimestamps(null)
          .blockUrls(null)
          .snapshotSource("web")
          .build();

      when(sessionReplayDao.queryBlockListing(eq(SESSION_ID)))
          .thenReturn(Single.just(listing));

      SnapshotSourcesResponse result = sessionReplayService.getBlockSources(SESSION_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getSources()).isEmpty();
      assertThat(result.getSnapshotSource()).isEqualTo("web");
    }

    @Test
    void shouldPropagateErrorWhenDaoFails() {
      when(sessionReplayDao.queryBlockListing(eq(SESSION_ID)))
          .thenReturn(Single.error(new RuntimeException("DAO error")));

      sessionReplayService.getBlockSources(SESSION_ID)
          .test()
          .assertError(RuntimeException.class)
          .assertError(throwable -> throwable.getMessage().contains("DAO"));
    }
  }

  @Nested
  class FetchBlockData {

    @Test
    void shouldReturnErrorWhenNoBlocksFound() {
      BlockListing listing = BlockListing.builder()
          .blockFirstTimestamps(List.of())
          .blockLastTimestamps(List.of())
          .blockUrls(List.of())
          .snapshotSource(null)
          .build();

      when(sessionReplayDao.queryBlockListing(eq(SESSION_ID)))
          .thenReturn(Single.just(listing));

      sessionReplayService.fetchBlockData(SESSION_ID, 0, 0)
          .test()
          .assertError(throwable ->
              throwable.getMessage() != null
                  && throwable.getMessage().contains("No replay blocks found for session"));
    }

    @Test
    void shouldReturnErrorWhenEndBlobKeyOutOfRange() {
      BlockListing listing = BlockListing.builder()
          .blockFirstTimestamps(List.of("2024-01-15T10:00:00Z"))
          .blockLastTimestamps(List.of("2024-01-15T10:01:00Z"))
          .blockUrls(List.of("s3://bucket/key?range=bytes=0-100"))
          .snapshotSource("mobile")
          .build();

      when(sessionReplayDao.queryBlockListing(eq(SESSION_ID)))
          .thenReturn(Single.just(listing));

      sessionReplayService.fetchBlockData(SESSION_ID, 0, 5)
          .test()
          .assertError(throwable ->
              throwable.getMessage() != null
                  && throwable.getMessage().contains("Block index out of range")
                  && throwable.getMessage().contains("end_blob_key=5")
                  && throwable.getMessage().contains("1 blocks exist"));
    }

    @Test
    void shouldFetchBlocksAndReturnConcatenatedData() {
      BlockListing listing = BlockListing.builder()
          .blockFirstTimestamps(List.of("2024-01-15T10:00:00Z", "2024-01-15T10:01:00Z"))
          .blockLastTimestamps(List.of("2024-01-15T10:01:00Z", "2024-01-15T10:02:00Z"))
          .blockUrls(List.of(
              "s3://bucket/key1?range=bytes=0-100",
              "s3://bucket/key2?range=bytes=101-200"))
          .snapshotSource("mobile")
          .build();

      byte[] expectedData = "{\"event\":1}\n{\"event\":2}".getBytes();

      when(sessionReplayDao.queryBlockListing(eq(SESSION_ID)))
          .thenReturn(Single.just(listing));
      when(sessionBlockFetcher.fetchBlocks(anyList())).thenReturn(Single.just(expectedData));

      byte[] result = sessionReplayService.fetchBlockData(SESSION_ID, 0, 1).blockingGet();

      assertThat(result).isEqualTo(expectedData);
      // Blocks sorted by firstTimestamp: 10:00 < 10:01, so key1 then key2
      verify(sessionBlockFetcher).fetchBlocks(List.of(
          "s3://bucket/key1?range=bytes=0-100",
          "s3://bucket/key2?range=bytes=101-200"));
    }

    @Test
    void shouldFetchSingleBlockWhenStartEqualsEnd() {
      BlockListing listing = BlockListing.builder()
          .blockFirstTimestamps(List.of("2024-01-15T10:00:00Z"))
          .blockLastTimestamps(List.of("2024-01-15T10:01:00Z"))
          .blockUrls(List.of("s3://bucket/key?range=bytes=0-100"))
          .snapshotSource("mobile")
          .build();

      byte[] expectedData = "{\"event\":1}\n".getBytes();

      when(sessionReplayDao.queryBlockListing(eq(SESSION_ID)))
          .thenReturn(Single.just(listing));
      when(sessionBlockFetcher.fetchBlocks(List.of("s3://bucket/key?range=bytes=0-100")))
          .thenReturn(Single.just(expectedData));

      byte[] result = sessionReplayService.fetchBlockData(SESSION_ID, 0, 0).blockingGet();

      assertThat(result).isEqualTo(expectedData);
      verify(sessionBlockFetcher).fetchBlocks(List.of("s3://bucket/key?range=bytes=0-100"));
    }

    @Test
    void shouldPropagateErrorWhenBlockFetcherFails() {
      BlockListing listing = BlockListing.builder()
          .blockFirstTimestamps(List.of("2024-01-15T10:00:00Z"))
          .blockLastTimestamps(List.of("2024-01-15T10:01:00Z"))
          .blockUrls(List.of("s3://bucket/key?range=bytes=0-100"))
          .snapshotSource("mobile")
          .build();

      when(sessionReplayDao.queryBlockListing(eq(SESSION_ID)))
          .thenReturn(Single.just(listing));
      when(sessionBlockFetcher.fetchBlocks(anyList()))
          .thenReturn(Single.error(new RuntimeException("S3 fetch failed")));

      sessionReplayService.fetchBlockData(SESSION_ID, 0, 0)
          .test()
          .assertError(RuntimeException.class)
          .assertError(throwable -> throwable.getMessage().contains("S3 fetch"));
    }
  }
}
