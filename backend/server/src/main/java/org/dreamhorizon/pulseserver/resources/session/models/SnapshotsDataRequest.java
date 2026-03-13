package org.dreamhorizon.pulseserver.resources.session.models;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for GET /v1/sessions/{sessionId}/snapshots-data query parameters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotsDataRequest {

  public static final int MAX_BLOB_KEY_RANGE = 20;

  @QueryParam("start_blob_key")
  @NotNull(message = "start_blob_key is required")
  @Min(value = 0, message = "start_blob_key must be >= 0")
  private Integer startBlobKey;

  @QueryParam("end_blob_key")
  @NotNull(message = "end_blob_key is required")
  @Min(value = 0, message = "end_blob_key must be >= 0")
  private Integer endBlobKey;

  @AssertTrue(message = "end_blob_key must be >= start_blob_key and range must not exceed " + MAX_BLOB_KEY_RANGE + " blocks")
  public boolean isValidBlobKeyRange() {
    if (startBlobKey == null || endBlobKey == null) {
      return true;
    }
    return startBlobKey <= endBlobKey && (endBlobKey - startBlobKey) < MAX_BLOB_KEY_RANGE;
  }
}
