import { useEffect, type FC } from "react";
import {
  flushPulseUserIdentityWhenReady,
  syncPulseUserIdentityFromCookies,
} from "./pulseRumAnalytics";

/**
 * Re-applies {@link Pulse.setUserId} from auth cookies after refresh / deep link,
 * and after async {@link Pulse.init} when login ran before the SDK was ready.
 * Must render inside {@link PulseProvider}.
 */
export const PulseRumUserSync: FC = () => {
  useEffect(() => {
    syncPulseUserIdentityFromCookies();
    void flushPulseUserIdentityWhenReady();
  }, []);

  return null;
};
