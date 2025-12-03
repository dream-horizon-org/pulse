package org.dreamhorizon.pulseserver.resources.alert.models;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SnoozeAlertRestResponse {
  private Boolean isSnoozed;
  private Long snoozedFrom;
  private Long snoozedUntil;
}
