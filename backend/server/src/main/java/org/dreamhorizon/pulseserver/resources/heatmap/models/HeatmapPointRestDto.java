package org.dreamhorizon.pulseserver.resources.heatmap.models;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapPointRestDto {
  private double x;
  private double y;
  private long weight;
}