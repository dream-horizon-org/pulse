package org.dreamhorizon.pulseserver.resources.v1.members.models;

/**
 * Machine-readable reason codes for bulk-invite failures.
 * Allows clients to distinguish constraint violations from transient errors
 * without parsing error message strings.
 */
public enum FailureReason {

  /** User already belongs to a different tenant — hard constraint violation. */
  CROSS_TENANT_VIOLATION,

  /** User is already a member of this tenant or project. */
  ALREADY_MEMBER,

  /** Transient or unexpected error (user creation failure, OpenFGA unavailable, etc.). */
  SYSTEM_ERROR;

  /**
   * Classify a throwable into one of the known reason codes.
   * Classification is based on exception type and message patterns that correspond
   * to the specific errors thrown by the service layer.
   */
  public static FailureReason from(Throwable error) {
    if (error == null || error.getMessage() == null) {
      return SYSTEM_ERROR;
    }
    String msg = error.getMessage();
    if (error instanceof IllegalStateException && msg.contains("different organization")) {
      return CROSS_TENANT_VIOLATION;
    }
    // MEMBER_ALREADY_EXISTS (ServiceError) produces a WebApplicationException whose
    // message contains the cause string set at the call site.
    if (msg.contains("already has role") || msg.contains("already a member")) {
      return ALREADY_MEMBER;
    }
    return SYSTEM_ERROR;
  }
}
