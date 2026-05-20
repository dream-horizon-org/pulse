package org.dreamhorizon.pulseserver.errorgrouping.dao;

/**
 * SQL constants for the {@code grouping_rule} table. Keeping the SQL out of
 * {@link GroupingRuleDao} matches the convention used by other DAOs in this
 * package (see {@code ClickhouseProjectCredentialsQueries}).
 */
public final class GroupingRuleQueries {

  private GroupingRuleQueries() {
    // utility class
  }

  /**
   * Fetch every enabled rule for a single project, sorted by kind then position
   * so the cache loader can stream straight into the per-kind lists without a
   * second sort pass.
   */
  public static final String GET_RULES_FOR_PROJECT =
      "SELECT id, project_id, rule_kind, pattern, replacement, position, enabled "
          + "FROM grouping_rule "
          + "WHERE project_id = ? AND enabled = TRUE "
          + "ORDER BY rule_kind, position ASC";
}
