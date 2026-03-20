package org.dreamhorizon.pulseserver.service.session;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.service.session.models.BlockListingQueryRow;
import org.dreamhorizon.pulseserver.resources.session.models.BlockSource;
import org.dreamhorizon.pulseserver.resources.session.models.SnapshotSourcesResponse;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.service.session.models.BlockListing;
import org.dreamhorizon.pulseserver.service.session.models.Block;
import org.dreamhorizon.pulseserver.service.session.query.SessionReplayQuery;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionReplayService {

  private final ClickhouseQueryService clickhouseQueryService;
  private final SessionBlockFetcher blockFetcher;

  
  public Single<SnapshotSourcesResponse> getBlockSources(String sessionId) {
    return queryBlockListing(sessionId)
        .map(listing -> buildSourcesResponse(sessionId, listing));
  }

  
  public Single<byte[]> fetchBlockData(String sessionId, int startBlobKey, int endBlobKey) {
    return queryBlockListing(sessionId)
        .flatMap(listing -> {
          List<Block> sortedBlocks = sortBlocks(listing);

          if (sortedBlocks.isEmpty()) {
            return Single.error(new IllegalArgumentException("No blocks found for session: " + sessionId));
          }
          if (endBlobKey >= sortedBlocks.size()) {
            return Single.error(new IllegalArgumentException(
                String.format("Block index out of range: end_blob_key=%d but only %d blocks exist",
                    endBlobKey, sortedBlocks.size())));
          }

          List<String> blockUrls = new ArrayList<>();
          for (int i = startBlobKey; i <= endBlobKey; i++) {
            blockUrls.add(sortedBlocks.get(i).getBlockUrl());
          }

          return blockFetcher.fetchBlocks(blockUrls);
        });
  }

  private Single<BlockListing> queryBlockListing(String sessionId) {
    String projectId = ProjectContext.requireProjectId();

    Map<String, Object> substitutionMap = new HashMap<>();
    substitutionMap.put("project_id", projectId);
    substitutionMap.put("session_id", sessionId);

    String formattedQuery = new StringSubstitutor(substitutionMap)
        .replace(SessionReplayQuery.GET_BLOCK_LISTING_QUERY);

    QueryConfiguration config = QueryConfiguration
        .newQuery(formattedQuery)
        .timeoutMs(5000)
        .projectId(projectId)
        .build();

    return clickhouseQueryService.executeQueryOrCreateJob(config, BlockListingQueryRow.class)
        .map(result -> {
          if (result.getRows() == null || result.getRows().isEmpty()) {
            return BlockListing.builder()
                .blockFirstTimestamps(List.of())
                .blockLastTimestamps(List.of())
                .blockUrls(List.of())
                .snapshotSource(null)
                .build();
          }
          BlockListingQueryRow row = result.getRows().get(0);
          return BlockListing.builder()
              .blockFirstTimestamps(parseStringArray(row.getBlockFirstTimestamps()))
              .blockLastTimestamps(parseStringArray(row.getBlockLastTimestamps()))
              .blockUrls(parseStringArray(row.getBlockUrls()))
              .snapshotSource(row.getSnapshotSource())
              .build();
        });
  }

  private SnapshotSourcesResponse buildSourcesResponse(String sessionId, BlockListing listing) {
    List<Block> sortedBlocks = sortBlocks(listing);

    List<BlockSource> sources = new ArrayList<>();
    for (int i = 0; i < sortedBlocks.size(); i++) {
      Block block = sortedBlocks.get(i);
      sources.add(BlockSource.builder()
          .source("blob")
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


  private List<String> parseStringArray(String arrayStr) {
    if (arrayStr == null || arrayStr.isEmpty() || "[]".equals(arrayStr)) {
      return List.of();
    }
    String inner = arrayStr;
    if (inner.startsWith("[")) {
      inner = inner.substring(1);
    }
    if (inner.endsWith("]")) {
      inner = inner.substring(0, inner.length() - 1);
    }
    List<String> result = new ArrayList<>();
    for (String element : inner.split(",")) {
      String trimmed = element.trim();
      if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
        trimmed = trimmed.substring(1, trimmed.length() - 1);
      }
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return result;
  }
}
