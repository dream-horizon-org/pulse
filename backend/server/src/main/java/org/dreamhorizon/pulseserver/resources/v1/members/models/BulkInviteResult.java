package org.dreamhorizon.pulseserver.resources.v1.members.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a bulk invite operation.
 * Contains success/failure counts and lists of emails by status.
 *
 * <p>{@code failedEmails} is retained for backward compatibility (format: "email (message)").
 * {@code structuredFailures} provides machine-readable per-entry failure details including
 * a typed {@link FailureReason} so callers can distinguish constraint violations from
 * transient errors without parsing strings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class BulkInviteResult implements AddMemberResult {
  private Integer successCount;                     // Number of successful invites
  private Integer failureCount;                     // Number of failed invites
  private Integer skippedCount;                     // Number of skipped emails (duplicates/already invited)
  private List<String> successEmails;               // List of successfully invited emails
  private List<String> failedEmails;                // Backward-compat: "email (message)" strings
  private List<String> skippedEmails;               // List of emails that were skipped
  private List<FailedInvite> structuredFailures;    // Machine-readable failures with typed reason codes
}
