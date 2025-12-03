package org.dreamhorizon.pulseserver.errorgrouping.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;


@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@EqualsAndHashCode
public class UploadMetadata {
  private String type;
  private String appVersion;
  private String fileName;
  private String platform;
  private String versionCode;

  @Override
  public String toString() {
    return String.format("UploadMetadata{type=%s, platform=%s, version=%s, versionCode=%s}",
        type, platform, appVersion, versionCode);
  }
}
