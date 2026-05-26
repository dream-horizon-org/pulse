import type { PulseEventAttributes } from "./pulseEventContext";

export type TrackPulseEventArgs = {
  action: string;
  label?: string;
  category?: string;
  value?: number;
  additionalParams?: Record<string, string | number | boolean>;
};

export type PulseUserIdentity = {
  userId: string;
  email?: string;
  name?: string;
  tenantId?: string;
  tenantRole?: string;
  systemRole?: string;
};

export type PendingPulseEvent = {
  name: string;
  attrs?: PulseEventAttributes;
};
