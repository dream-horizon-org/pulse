package org.dreamhorizon.pulseserver.dao.interaction;

public class Queries {
  public static final String ARCHIVE_JOB = "UPDATE interaction SET is_archived = 1,"
      + " updated_by = ? WHERE name = ? AND is_archived = 0";

  public static final String AUDIT_INTERACTION_CHANGES = "insert into "
      + "interaction_details_changes (interaction_id, name, description, status, is_archived, interaction_details, updated_by) "
      + "select interaction_id, name, description, status, is_archived, interaction_details, updated_by "
      + "from interaction where interaction_id = ?;";

  public static final String GET_COUNT_OF_INTERACTION_NAME_NAME = "SELECT COUNT(*) AS count FROM interaction "
      + "WHERE name = ? AND is_archived = 0";
  public static final String INSERT_INTERACTION = "INSERT INTO interaction "
      + "(name, status, details, is_archived, created_at, created_by, last_updated_at, updated_by) "
      + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
  public static final String GET_INTERACTION_DETAILS = "SELECT "
      + "interaction_id, name, status, details, created_at, created_by, last_updated_at, updated_by "
      + " from interaction where name = ? and is_archived = 0";
  public static final String UPDATE_INTERACTION = "UPDATE interaction SET "
      + "status = ?, details = ?, last_updated_at = ?, updated_by = ? "
      + " WHERE interaction_id = ?";
  public static final String GET_INTERACTIONS = "SELECT interaction_id, name, "
      + " created_by, updated_by, created_at, last_updated_at, status, details, "
      + " COUNT(*) OVER() AS total_interactions FROM interaction "
      + " WHERE is_archived = 0 ";
  public static final String GET_ALL_ACTIVE_AND_RUNNING_INTERACTIONS = "SELECT interaction_id, name, "
      + " created_by, updated_by, created_at, last_updated_at, status, details "
      + " FROM interaction WHERE is_archived = 0 "
      + " AND UPPER(status) = UPPER('RUNNING')";
  public static final String GET_INTERACTION_FILTER_OPTIONS = "SELECT "
      + "DISTINCT status, created_by "
      + "FROM interaction "
      + "WHERE is_archived = 0 "
      + "ORDER BY status, created_by";

  public static final String GET_TELEMETRY_FILTER_VALUES =
      "SELECT\n"
          + " arraySort(arrayFilter(x -> x != '', groupUniqArray(AppVersion)))      AS appVersionCodes,\n"
          + " arraySort(arrayFilter(x -> x != '', groupUniqArray(DeviceModel)))      AS deviceModels,\n"
          + " arraySort(arrayFilter(x -> x != '', groupUniqArray(NetworkProvider)))  AS networkProviders,\n"
          + " arraySort(arrayFilter(x -> x != '', groupUniqArray(GeoState)))         AS states,\n"
          + " arraySort(arrayFilter(x -> x != '', groupUniqArray(OsVersion)))        AS osVersions,\n"
          + " arraySort(arrayFilter(x -> x != '', groupUniqArray(Platform)))         AS platforms\n"
          + " FROM otel.otel_traces;";
}