package org.dreamhorizon.pulseserver.service.notification.models;

public enum NotificationStatus {
  PENDING,
  QUEUED,
  PROCESSING,
  SENT,
  DELIVERED,
  FAILED,
  RETRYING,
  SKIPPED,
  PERMANENT_FAILURE,
  BOUNCED,
  COMPLAINED
}
