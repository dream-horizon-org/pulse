package org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickhouseCredentials {
  private Long id;
  private String tenantId;
  private String clickhouseUsername;
  private transient String clickhousePassword;
  private String encryptionSalt;
  private String passwordDigest;
  private Boolean isActive;
  private String createdAt;
  private String updatedAt;
}
