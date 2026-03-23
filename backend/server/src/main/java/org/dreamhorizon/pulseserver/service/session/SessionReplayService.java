package org.dreamhorizon.pulseserver.service.session;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.sessionreplay.SessionReplayDao;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.session.models.BlockSource;
import org.dreamhorizon.pulseserver.resources.session.models.SnapshotSourcesResponse;
import org.dreamhorizon.pulseserver.service.session.models.BlockListing;
import org.dreamhorizon.pulseserver.service.session.models.Block;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionReplayService {

  private final SessionReplayDao sessionReplayDao;
  private final SessionBlockFetcher sessionBlockFetcher;

  private static final String BLOB = "blob";

  public Single<SnapshotSourcesResponse> getBlockSources(String sessionId) {
    return sessionReplayDao.queryBlockListing(sessionId)
        .map(listing -> buildSourcesResponse(sessionId, listing));
  }

  
  public Single<byte[]> fetchBlockData(String sessionId, int startBlobKey, int endBlobKey) {
    return sessionReplayDao.queryBlockListing(sessionId)
        .flatMap(listing -> {
          List<Block> sortedBlocks = sortBlocks(listing);

          if (sortedBlocks.isEmpty()) {
            return Single.error(
                ServiceError.NOT_FOUND.getCustomException(
                    "No replay blocks found for session: " + sessionId));
          }
          if (endBlobKey >= sortedBlocks.size()) {
            return Single.error(
                ServiceError.INVALID_REQUEST_PARAM.getCustomException(
                    String.format(
                        "Block index out of range: end_blob_key=%d but only %d blocks exist",
                        endBlobKey, sortedBlocks.size())));
          }

          List<String> blockUrls = new ArrayList<>();
          for (int i = startBlobKey; i <= endBlobKey; i++) {
            blockUrls.add(sortedBlocks.get(i).getBlockUrl());
          }

          return sessionBlockFetcher.fetchBlocks(blockUrls);
        });
  }

  private SnapshotSourcesResponse buildSourcesResponse(String sessionId, BlockListing listing) {
    List<Block> sortedBlocks = sortBlocks(listing);

    List<BlockSource> sources = new ArrayList<>();
    for (int i = 0; i < sortedBlocks.size(); i++) {
      Block block = sortedBlocks.get(i);
      sources.add(BlockSource.builder()
          .source(BLOB)
          .blobKey(String.valueOf(i))
          .startTimestamp(block.getFirstTimestamp())
          .endTimestamp(block.getLastTimestamp())
          .build());
    }

    return SnapshotSourcesResponse.builder()
        .sessionId(sessionId)
        .snapshotSource(listing.getSnapshotSource())
        .sources(sources)
        .build();
  }

  private List<Block> sortBlocks(BlockListing listing) {
    if (listing.getBlockUrls() == null || listing.getBlockUrls().isEmpty()) {
      return List.of();
    }

    List<Block> blocks = new ArrayList<>();
    for (int i = 0; i < listing.getBlockUrls().size(); i++) {
      blocks.add(Block.builder()
          .firstTimestamp(listing.getBlockFirstTimestamps().get(i))
          .lastTimestamp(listing.getBlockLastTimestamps().get(i))
          .blockUrl(listing.getBlockUrls().get(i))
          .build());
    }

    blocks.sort(Comparator.comparing(Block::getFirstTimestamp));
    return blocks;
  }
}
