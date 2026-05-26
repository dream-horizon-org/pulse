package org.dreamhorizon.pulsespark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.dreamhorizon.pulsespark.model.FunnelDefinition;
import org.dreamhorizon.pulsespark.model.FunnelFilter;
import org.dreamhorizon.pulsespark.model.FunnelStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.spark.sql.functions.*;

public final class FunnelFilterUtils {

  private static final Logger log = LoggerFactory.getLogger(FunnelFilterUtils.class);

  private FunnelFilterUtils() {}

  public static Dataset<Row> applyFilters(Dataset<Row> df, List<FunnelFilter> filters) {
    for (var filter : filters) {
      if (filter.field() == null || filter.field().isBlank()) {
        log.warn("Skipping filter: field is null or blank");
        continue;
      }
      if (filter.value().isEmpty()) {
        log.warn("Skipping filter: empty value list for field '{}'", filter.field());
        continue;
      }
      df = df.filter(buildColumnCondition(filter));
    }
    return df;
  }

  /**
   * Builds a Spark {@link Column} expression for a single {@link FunnelFilter}.
   * Extracted so both {@link #applyFilters} and {@link #preFilterToFunnelSteps} share the
   * same operator logic without duplication.
   */
  private static Column buildColumnCondition(FunnelFilter filter) {
    var fieldCol = FilterFieldMapper.toColumn(filter.field());
    var vals = filter.value().toArray();
    String op = FunnelFilterOperators.normalize(filter.operator());
    return switch (op) {
      case "EQ", "=", "IN" -> fieldCol.isin(vals);
      case "NE", "!=", "NOT_IN" -> not(fieldCol.isin(vals));
      case "CONTAINS" -> filter.value().stream()
        .map(fieldCol::contains)
        .reduce(Column::or)
        .orElse(lit(true));
      default -> {
        log.warn("Unknown filter operator '{}' (normalized='{}') for field '{}' — filter skipped",
          filter.operator(), op, filter.field());
        yield lit(true);
      }
    };
  }

  /**
   * Collects all filter fields across all funnels (global + step) that are custom event
   * properties — i.e. not already top-level catalog columns. These fields must be uplifted
   * from {@code log_attributes} before the map is dropped.
   */
  static Set<String> collectLookupFields(List<FunnelDefinition> funnels) {
    Set<String> fields = new HashSet<>();
    for (var funnel : funnels) {
      funnel.globalFilters().stream()
        .map(FunnelFilter::field)
        .filter(f -> f != null && !f.isBlank() && !FilterFieldMapper.isKnownCatalogKey(f))
        .forEach(fields::add);
      funnel.steps().forEach(step -> step.stepFilters().stream()
        .map(FunnelFilter::field)
        .filter(f -> f != null && !f.isBlank() && !FilterFieldMapper.isKnownCatalogKey(f))
        .forEach(fields::add));
    }
    return fields;
  }

  /**
   * Uplifts each custom-property field from {@code log_attributes} to a top-level column,
   * then drops the map entirely. This shrinks the cached dataset significantly when
   * {@code log_attributes} carries many unused keys.
   */
  static Dataset<Row> upliftAndDrop(Dataset<Row> df, Set<String> fields) {
    for (var field : fields) {
      df = df.withColumn(field, col(SparkConstants.Columns.LOG_ATTRIBUTES).getItem(field));
    }
    return df.drop(SparkConstants.Columns.LOG_ATTRIBUTES);
  }

  /**
   * Pre-filters {@code df} to only rows that can participate in any step of any funnel.
   * The predicate is the OR of each funnel's OR-of-steps condition:
   * <pre>
   *   (f1_step0_event AND f1_step0_filters) OR … OR (fN_stepM_event AND fN_stepM_filters)
   * </pre>
   * Uses {@link #buildColumnCondition} so operator logic is not duplicated.
   */
  static Dataset<Row> preFilterForFunnels(Dataset<Row> df, List<FunnelDefinition> funnels) {
    Column combined = null;
    for (var funnel : funnels) {
      Column funnelCond = null;
      for (var step : funnel.steps()) {
        if (step.eventName() == null || step.eventName().isBlank()) continue;
        Column stepCond = col(SparkConstants.Columns.EVENT_NAME).equalTo(step.eventName());
        for (var f : step.stepFilters()) {
          if (f.field() == null || f.field().isBlank() || f.value().isEmpty()) continue;
          stepCond = stepCond.and(buildColumnCondition(f));
        }
        funnelCond = (funnelCond == null) ? stepCond : funnelCond.or(stepCond);
      }
      if (funnelCond != null) {
        combined = (combined == null) ? funnelCond : combined.or(funnelCond);
      }
    }
    return combined != null ? df.filter(combined) : df;
  }

  /**
   * Narrows {@code df} to only rows that can participate in any funnel step. The predicate is:
   * <pre>
   *   (event_name = step0 AND step0_filters) OR (event_name = step1 AND step1_filters) OR …
   * </pre>
   * A row that matches no step's event name + filters can never contribute to funnel counting,
   * session_state, user_state, or attribution, so excluding it from the cache is safe.
   */
  static Dataset<Row> preFilterToFunnelSteps(Dataset<Row> df, List<FunnelStep> steps) {
    if (steps.isEmpty()) return df;
    Column condition = null;
    for (var step : steps) {
      Column stepCond = col(SparkConstants.Columns.EVENT_NAME).equalTo(step.eventName());
      for (var f : step.stepFilters()) {
        if (f.field() == null || f.field().isBlank() || f.value().isEmpty()) continue;
        stepCond = stepCond.and(buildColumnCondition(f));
      }
      condition = (condition == null) ? stepCond : condition.or(stepCond);
    }
    return condition != null ? df.filter(condition) : df;
  }

  /**
   * Narrows {@code raw} to the project's custom-event rows that funnels and journeys actually
   * consume. Without the {@code pulse_type = 'custom_event'} filter we'd include
   * session-lifecycle, jank, web-vital, crash etc. log rows that share the same
   * {@code otel_logs} parquet — which is what ClickHouse's {@code buildInsertSqlWindowFunnel}
   * explicitly excludes via {@code WHERE PulseType = 'custom_event'}. Without this filter on
   * Spark, the chain walker matches non-custom rows that happen to share an EventName, OR
   * misses rows whose PulseType isn't what the funnel ingester expects — leading to the
   * "0 count for HomeLoaded" symptom even when the events are demonstrably in S3.
   */
  static Dataset<Row> prepareRaw(Dataset<Row> ds, String projectId) {
    Dataset<Row> filtered = ds.filter(col(SparkConstants.Columns.PROJECT_ID).equalTo(projectId))
      .filter(col(SparkConstants.Columns.PULSE_TYPE).equalTo(lit(SparkConstants.PulseTypes.CUSTOM_EVENT)));
    if (log.isInfoEnabled()) {
      long count = filtered.count();
      log.info("prepareRaw: project={} pulse_type={} → {} rows",
        projectId, SparkConstants.PulseTypes.CUSTOM_EVENT, count);
      if (count == 0) {
        log.warn(
          "prepareRaw: 0 rows after pulse_type='{}' filter for project {}. " +
            "Either the SDK isn't emitting custom events with this PulseType, " +
            "or the otel_logs partitions for this window contain only non-custom rows.",
          SparkConstants.PulseTypes.CUSTOM_EVENT, projectId);
      }
    }
    return filtered;
  }
}
