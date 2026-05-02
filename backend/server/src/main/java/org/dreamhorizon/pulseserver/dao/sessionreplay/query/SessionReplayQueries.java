package org.dreamhorizon.pulseserver.dao.sessionreplay.query;

public class SessionReplayQueries {

  public static final String GET_BLOCK_LISTING_QUERY = """
      SELECT
          min(MinFirstTimestamp) AS start_time,
          toString(groupArrayArray(BlockFirstTimestamps)) AS block_first_timestamps,
          toString(groupArrayArray(BlockLastTimestamps)) AS block_last_timestamps,
          toString(groupArrayArray(BlockUrls)) AS block_urls,
          any(SnapshotSource) AS snapshot_source
      FROM otel.session_replay_events
      WHERE ProjectId = '${project_id}'
        AND SessionId = '${session_id}'
      GROUP BY SessionId
      """;
}
