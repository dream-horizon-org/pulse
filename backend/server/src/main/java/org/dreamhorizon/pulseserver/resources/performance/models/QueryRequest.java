package org.dreamhorizon.pulseserver.resources.performance.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class QueryRequest {
  /**
   * e.g., "traces"
   */
  private DataType dataType;

  private TimeRange timeRange;

  /**
   * SELECT-like list
   */
  private List<SelectItem> select;

  /**
   * WHERE-like list
   */
  private List<Filter> filters;

  /**
   * GROUP BY fields
   */
  private List<String> groupBy;

  /**
   * ORDER BY list
   */
  private List<OrderBy> orderBy;

  /**
   * LIMIT
   */
  private Integer limit;


  // ---------- Nested types ----------

  public enum Direction {
    ASC, DESC
  }

  public enum DataType {
    TRACES,
    LOGS,
    METRICS,
    EXCEPTIONS
  }

  @Getter
  public enum Operator {
    LIKE("like"),
    IN("In"),
    EQ("="),
    ADDITIONAL("");

    private final String displayName;

    Operator(String displayName) {
      this.displayName = displayName;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  public static class TimeRange {
    /**
     * ISO-8601 instants, e.g. 2025-11-07T08:40:00Z
     */
    private String start;
    private String end;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  public static class SelectItem {
    /**
     * e.g., "col", "duration_p99", "apdex", "time_bucket", "custom"
     */
    private Functions function;

    /**
     * Optional function parameters, varies by function
     */
    private Map<String, String> param;

    /**
     * Optional alias for the computed/selected column
     */
    private String alias;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  public static class Filter {
    /**
     * e.g., "span.name"
     */
    private String field;

    /**
     * e.g., "IN", "LIKE", "=", etc.
     */
    private Operator operator;

    /**
     * Values for the filter; JSON shows arrays, so we model as list
     */
    private List<Object> value;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  public static class OrderBy {
    /**
     * Field/alias to sort by (e.g., "t1", "apdex")
     */
    private String field;

    /**
     * "ASC" or "DESC"
     */
    private Direction direction;

    public OrderBy() {
    }

  }
}
