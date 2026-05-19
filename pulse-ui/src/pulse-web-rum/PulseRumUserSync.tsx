import { useEffect, type FC } from "react";
import { syncPulseUserIdentityFromCookies } from "./pulseRumAnalytics";

/**
 * Re-applies {@link Pulse.setUserId} from auth cookies after refresh / deep link.
 * Must render inside {@link PulseProvider}.
 */
export const PulseRumUserSync: FC = () => {
  useEffect(() => {
    syncPulseUserIdentityFromCookies();
  }, []);

  return null;
};
