package org.dreamhorizon.pulseserver.service.session.query;

public class SessionReplayQuery {

  public static final String GET_BLOCK_LISTING_QUERY = """
      SELECT
          min(min_first_timestamp) AS start_time,
          toString(groupArrayArray(block_first_timestamps)) AS block_first_timestamps,
          toString(groupArrayArray(block_last_timestamps)) AS block_last_timestamps,
          toString(groupArrayArray(block_urls)) AS block_urls,
          argMinMerge(snapshot_source) AS snapshot_source
      FROM otel.session_replay_events
      WHERE project_id = '${project_id}'
        AND session_id = '${session_id}'
      GROUP BY session_id
      """;
}
