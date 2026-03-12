package org.dreamhorizon.pulseserver.resources.v1.members.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a bulk invite operation.
 * Contains success/failure counts and lists of emails by status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkInviteResult {
  private Integer successCount;        // Number of successful invites
  private Integer failureCount;        // Number of failed invites
  private Integer skippedCount;        // Number of skipped emails (duplicates/already invited)
  private List<String> successEmails;  // List of successfully invited emails
  private List<String> failedEmails;   // List of emails that failed with reasons
  private List<String> skippedEmails;  // List of emails that were skipped
}
