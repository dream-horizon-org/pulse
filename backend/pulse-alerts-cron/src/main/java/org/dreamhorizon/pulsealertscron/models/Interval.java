package org.dreamhorizon.pulsealertscron.models;


import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class Interval {
  public int intervalLengthInMins;
}