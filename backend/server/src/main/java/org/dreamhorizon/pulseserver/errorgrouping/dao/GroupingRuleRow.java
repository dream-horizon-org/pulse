package org.dreamhorizon.pulseserver.errorgrouping.dao;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Immutable representation of a single row of the {@code grouping_rule} table.
 *
 * <p>The DAO maps each MySQL row to this carrier; the cache layer then partitions
 * a {@code List<GroupingRuleRow>} by {@link #getRuleKind()} into the
 * {@code GroupingRules} bundle consumed by the heuristic pipeline. The shape
 * mirrors the columns 1:1 — no derived state lives here.</p>
 *
 * <p>{@code replacement} is only meaningful for {@code MASK_REGEX} rows and may
 * be {@code null} for every other kind.</p>
 */
@Getter
@Builder
@ToString
@AllArgsConstructor
public final class GroupingRuleRow {

  private final Long id;
  private final String projectId;
  private final String ruleKind;
  private final String pattern;
  private final String replacement;
  private final int position;
  private final boolean enabled;
}
