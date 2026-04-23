package org.dreamhorizon.pulseserver.resources.productAnalysis.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Filter option metadata included in listing responses so the UI can
 * populate "Created by" and "Tags" filter dropdowns without extra API calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListFilterOptions {

  /** Distinct creator emails/names across all entities in the project. */
  private List<String> creators;

  /** Distinct tags across all entities in the project. */
  private List<String> tags;
}
