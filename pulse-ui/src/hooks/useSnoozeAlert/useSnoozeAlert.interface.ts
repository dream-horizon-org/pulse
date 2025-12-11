/**
 * Request body for snooze - matches backend SnoozeAlertRestRequest.java
 * Uses camelCase as per backend
 */
export type SnoozeAlertRequest = {
  snoozeFrom: number;  // Unix timestamp in milliseconds
  snoozeUntil: number; // Unix timestamp in milliseconds
};

export type SnoozeAlertInput = {
  alertId: string;
  snoozeAlertRequest: SnoozeAlertRequest;
};

/**
 * Response - matches backend SnoozeAlertRestResponse.java
 */
export type SnoozeAlertResponse = {
  isSnoozed: boolean;
  snoozedFrom: number;
  snoozedUntil: number;
};
