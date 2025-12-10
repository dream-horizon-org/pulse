export type SnoozeAlertRequest = {
  snoozed_from: number;
  snoozed_until: number;
};

export type SnoozeAlertInput = {
  alertId: string;
  snoozeAlertRequest: SnoozeAlertRequest;
};

export type SnoozeAlertResponse = {
  alert_id: number;
  is_snoozed: boolean;
  snoozed_from: number;
  snoozed_until: number;
};


