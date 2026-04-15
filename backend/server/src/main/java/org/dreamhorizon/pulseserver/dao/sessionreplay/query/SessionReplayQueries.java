package org.dreamhorizon.pulseserver.dao.sessionreplay.query;

public class SessionReplayQueries {

  public static final String GET_BLOCK_LISTING_QUERY = """
      SELECT
          min(MinFirstTimestamp) AS start_time,
          toString(groupArrayArray(BlockFirstTimestamps)) AS block_first_timestamps,
          toString(groupArrayArray(BlockLastTimestamps)) AS block_last_timestamps,
          toString(groupArrayArray(BlockUrls)) AS block_urls,
          argMinMerge(SnapshotSource) AS snapshot_source
      FROM otel.session_replay_events
      WHERE SessionId = '${session_id}'
      GROUP BY SessionId
      """;
}
