package org.dreamhorizon.pulseserver.dao.rcajob;

/** RCA report types supported by the async job system. */
public enum RcaType {
  INTERACTION,
  SESSION,
  SCREEN,
  SCREEN_V2
  // Future: EVENT, USER_SEGMENT, etc.
}
