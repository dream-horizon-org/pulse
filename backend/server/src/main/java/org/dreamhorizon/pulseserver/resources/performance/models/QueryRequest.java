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
  private DataType dataType;

  private TimeRange timeRange;

  private List<SelectItem> select;

  private List<Filter> filters;

  private List<String> groupBy;

  private List<OrderBy> orderBy;

  private Integer limit;

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
    private String start;
    private String end;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  public static class SelectItem {
    private Functions function;

    private Map<String, String> param;

    private String alias;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  public static class Filter {
    private String field;

    private Operator operator;

    private List<Object> value;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  public static class OrderBy {
    private String field;

    private Direction direction;

    public OrderBy() {
    }

  }
}
