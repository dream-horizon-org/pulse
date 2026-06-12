package org.dreamhorizon.pulseserver.dto.response;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class GetRawUserEventsResponseDto {

  @JsonProperty("schema")
  private Schema schema;

  @JsonProperty("totalRows")
  private Long totalRows;

  @JsonProperty("rows")
  private List<Row> rows;

  @JsonProperty("creationTime")
  private String creationTime;

  @JsonProperty("startTime")
  private String startTime;

  @JsonProperty("endTime")
  private String endTime;

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Schema {
    @JsonProperty("fields")
    private List<Field> fields;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Field {
    @JsonProperty("name")
    private String name;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Row {
    @JsonProperty("f")
    private List<RowField> rowFields;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RowField {
    @JsonProperty("v")
    private Object value;
  }

}
