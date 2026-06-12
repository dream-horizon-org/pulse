package org.dreamhorizon.pulsealertscron.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor
public enum ServiceError {
  SERVICE_UNKNOWN_EXCEPTION("pulse-alerts-cron-UNKNOWN-EXCEPTION", "Something went wrong", 500);

  final String errorCode;
  final String errorMessage;
  final int httpStatusCode;
}

