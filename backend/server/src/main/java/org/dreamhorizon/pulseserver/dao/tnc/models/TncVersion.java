package org.dreamhorizon.pulseserver.dao.tnc.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TncVersion {
  private Long id;
  private String version;
  private String tosS3Url;
  private String aupS3Url;
  private String privacyPolicyS3Url;
  private String summary;
  private boolean active;
  private String publishedAt;
  private String createdBy;
  private String createdAt;
}
