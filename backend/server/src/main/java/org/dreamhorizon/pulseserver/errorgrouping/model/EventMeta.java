package org.dreamhorizon.pulseserver.errorgrouping.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMeta {
  private String platform;     // "android" / "ios" / "web" (optional)
  private String appVersion;      // "6.1.0-100034" (or split into name+code)
  private String appVersionCode;
  private String bundleId;
}
