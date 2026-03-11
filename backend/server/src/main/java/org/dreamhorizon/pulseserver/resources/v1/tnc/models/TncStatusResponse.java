package org.dreamhorizon.pulseserver.resources.v1.tnc.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TncStatusResponse {
  private boolean accepted;
  private String version;
  private Long versionId;
  private TncDocumentUrls documents;
  private String acceptedBy;
  private String acceptedAt;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TncDocumentUrls {
    private String tos;
    private String aup;

    @JsonProperty("privacy_policy")
    private String privacyPolicy;
  }
}
