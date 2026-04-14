package org.dreamhorizon.pulseserver.dao.rcajob;

/** Lifecycle state for an RCA report generation job in MySQL. */
public enum RcaJobStatus {
  PENDING,
  PROCESSING,
  COMPLETED,
  FAILED
}
