package org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateRevenueEventRequest {

  @NotBlank
  private String eventName;

  @NotBlank
  private String valueAttribute;

  private String currency;

  private String currencyAttribute;

  @NotNull
  @Min(1)
  private Integer conversionWindowHours;
}
