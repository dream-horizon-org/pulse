package org.dreamhorizon.pulseserver.dao.productAnalysis.funneljourneytag;

import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;

public final class FunnelJourneyTagQueries {

  static final String DELETE_ALL_FOR_ENTITY =
      "DELETE FROM funnel_journey_tag WHERE project_id = ? AND entity_type = ? AND entity_id = ?";

  static final String SELECT_TAGS_FOR_ENTITY =
      "SELECT tag FROM funnel_journey_tag WHERE project_id = ? AND entity_type = ? AND entity_id = ? ORDER BY tag";

  private FunnelJourneyTagQueries() {}

  static String buildSelectTagsForEntitiesIn(int placeholderCount) {
    StringBuilder placeholders = new StringBuilder();
    for (int i = 0; i < placeholderCount; i++) {
      if (i > 0) {
        placeholders.append(',');
      }
      placeholders.append('?');
    }
    return "SELECT entity_id, tag FROM funnel_journey_tag WHERE project_id = ? AND entity_type = ?"
        + " AND entity_id IN ("
        + placeholders
        + ") ORDER BY entity_id, tag";
  }

  static String buildBatchInsert(int rowCount) {
    StringBuilder sb =
        new StringBuilder(
            "INSERT INTO funnel_journey_tag (project_id, entity_type, entity_id, tag) VALUES ");
    for (int i = 0; i < rowCount; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append("(?,?,?,?)");
    }
    return sb.toString();
  }

  static Tuple batchInsertTuple(
      String projectId,
      FunnelJourneyTagEntityType entityType,
      long entityId,
      List<String> tags) {
    String et = entityType.name();
    List<Object> vals = new ArrayList<>(tags.size() * 4);
    for (String t : tags) {
      vals.add(projectId);
      vals.add(et);
      vals.add(entityId);
      vals.add(t);
    }
    return Tuple.from(vals);
  }
}
