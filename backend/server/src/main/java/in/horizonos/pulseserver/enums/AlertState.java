package in.horizonos.pulseserver.enums;

import lombok.Getter;

@Getter
public enum AlertState {
  NORMAL("NORMAL"),
  FIRING("FIRING"),
  SILENCED("SILENCED"),
  NO_DATA("NO_DATA"),
  ERRORED("ERRORED"),
  QUERY_FAILED("QUERY_FAILED");

  private final String alertState;

  AlertState(String alertState) {
    this.alertState = alertState;
  }

  @Override
  public String toString() {
    return alertState;
  }
}
