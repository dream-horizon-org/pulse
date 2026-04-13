package org.dreamhorizon.pulseserver.resources.v1.members.models;

import lombok.Builder;
import lombok.Value;

/**
 * Structured representation of a single failed bulk-invite entry.
 * Carries both a machine-readable {@link FailureReason} and the original error message.
 */
@Value
@Builder
public class FailedInvite {
  String email;
  FailureReason reason;
  String message;
}
