package org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row from {@code SELECT FilterValue AS name ... event_catalog_entries}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventCatalogEventNameRow {

  @JsonProperty("name")
  private String name;
}
