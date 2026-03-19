package org.dreamhorizon.pulseserver.dao.eventdefinition;

public class EventDefinitionQueries {

  private static final String SELECT_COLUMNS =
      "SELECT ed.id, ed.event_name, ed.display_name, ed.description, ed.category, ed.is_archived,"
          + " ed.created_by, ed.updated_by, ed.created_at, ed.updated_at"
          + " FROM event_definitions ed";

  private static final String SEARCH_CLAUSE =
      " AND (ed.event_name LIKE CONCAT('%', ?, '%')"
          + " OR ed.description LIKE CONCAT('%', ?, '%')"
          + " OR ed.category LIKE CONCAT('%', ?, '%'))";

  private static final String CATEGORY_CLAUSE =
      " AND ed.category = ?";

  public static String buildListQuery(boolean hasSearch, boolean hasCategory) {
    StringBuilder sb = new StringBuilder(SELECT_COLUMNS);
    sb.append(" WHERE ed.project_id = ? AND ed.is_archived = FALSE");
    if (hasSearch) {
      sb.append(SEARCH_CLAUSE);
    }
    if (hasCategory) {
      sb.append(CATEGORY_CLAUSE);
    }
    sb.append(" ORDER BY ed.event_name LIMIT ? OFFSET ?");
    return sb.toString();
  }

  public static String buildCountQuery(boolean hasSearch, boolean hasCategory) {
    StringBuilder sb = new StringBuilder(
        "SELECT COUNT(*) AS total_count FROM event_definitions ed"
            + " WHERE ed.project_id = ? AND ed.is_archived = FALSE");
    if (hasSearch) {
      sb.append(SEARCH_CLAUSE);
    }
    if (hasCategory) {
      sb.append(CATEGORY_CLAUSE);
    }
    return sb.toString();
  }

  public static final String GET_DISTINCT_CATEGORIES =
      "SELECT DISTINCT category FROM event_definitions"
          + " WHERE project_id = ? AND is_archived = FALSE"
          + " AND category IS NOT NULL AND category != ''"
          + " ORDER BY category";

  public static final String GET_EVENT_DEFINITION_BY_ID =
      "SELECT ed.id, ed.event_name, ed.display_name, ed.description, ed.category, ed.is_archived,"
          + " ed.created_by, ed.updated_by, ed.created_at, ed.updated_at"
          + " FROM event_definitions ed"
          + " WHERE ed.id = ? AND ed.project_id = ?";

  public static final String INSERT_EVENT_DEFINITION =
      "INSERT INTO event_definitions"
          + " (project_id, event_name, display_name, description, category, created_by, updated_by)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?)";

  public static final String UPSERT_EVENT_DEFINITION =
      "INSERT INTO event_definitions"
          + " (project_id, event_name, display_name, description, category, created_by, updated_by)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?)"
          + " ON DUPLICATE KEY UPDATE"
          + " display_name = VALUES(display_name),"
          + " description = VALUES(description),"
          + " category = VALUES(category),"
          + " updated_by = VALUES(updated_by),"
          + " updated_at = CURRENT_TIMESTAMP";

  public static final String UPDATE_EVENT_DEFINITION =
      "UPDATE event_definitions SET"
          + " display_name = ?, description = ?, category = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP"
          + " WHERE id = ? AND project_id = ?";

  public static final String ARCHIVE_EVENT_DEFINITION =
      "UPDATE event_definitions SET is_archived = TRUE, updated_by = ?, updated_at = CURRENT_TIMESTAMP"
          + " WHERE id = ? AND project_id = ?";

  public static final String GET_ATTRIBUTES_FOR_EVENT =
      "SELECT ead.id, ead.event_definition_id, ead.attribute_name, ead.description,"
          + " ead.data_type, ead.is_required, ead.is_archived"
          + " FROM event_attribute_definitions ead"
          + " WHERE ead.event_definition_id = ?"
          + " AND ead.is_archived = FALSE";

  public static String getAttributesForEventsBatch(int count) {
    StringBuilder sb = new StringBuilder(
        "SELECT ead.id, ead.event_definition_id, ead.attribute_name, ead.description,"
            + " ead.data_type, ead.is_required, ead.is_archived"
            + " FROM event_attribute_definitions ead"
            + " WHERE ead.event_definition_id IN (");
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append("?");
    }
    sb.append(") AND ead.is_archived = FALSE");
    return sb.toString();
  }

  public static final String INSERT_ATTRIBUTE =
      "INSERT INTO event_attribute_definitions"
          + " (event_definition_id, attribute_name, description, data_type, is_required)"
          + " VALUES (?, ?, ?, ?, ?)";

  public static final String UPSERT_ATTRIBUTE =
      "INSERT INTO event_attribute_definitions"
          + " (event_definition_id, attribute_name, description, data_type, is_required)"
          + " VALUES (?, ?, ?, ?, ?)"
          + " ON DUPLICATE KEY UPDATE"
          + " description = VALUES(description),"
          + " data_type = VALUES(data_type),"
          + " is_required = VALUES(is_required),"
          + " is_archived = FALSE";

  public static final String DELETE_ATTRIBUTES_FOR_EVENT =
      "DELETE FROM event_attribute_definitions WHERE event_definition_id = ?";

  public static final String GET_EVENT_DEFINITION_ID_BY_NAME =
      "SELECT id FROM event_definitions WHERE project_id = ? AND event_name = ?";

  private EventDefinitionQueries() {
  }
}
