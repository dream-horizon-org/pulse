import { useEffect, useRef } from "react";
import {
  trackPulseEvent,
  type PulseEventAttributes,
} from "./pulseRumAnalytics";

type UseTrackScreenLoadedOnceOptions = {
  eventName: string;
  attrs?: PulseEventAttributes;
  /** When false, tracking is skipped (e.g. still loading). */
  ready: boolean;
  /** Reset tracking when this changes (e.g. project switch). */
  resetKey?: string | null;
};

/**
 * Fires a screen-loaded custom event once per mount / resetKey — for interaction
 * sequences like nav_item_clicked → interactions_list_loaded.
 */
export function useTrackScreenLoadedOnce({
  eventName,
  attrs,
  ready,
  resetKey,
}: UseTrackScreenLoadedOnceOptions): void {
  const trackedRef = useRef(false);

  useEffect(() => {
    trackedRef.current = false;
  }, [resetKey]);

  useEffect(() => {
    if (!ready || trackedRef.current) return;
    trackedRef.current = true;
    trackPulseEvent(eventName, attrs);
  }, [ready, eventName, attrs, resetKey]);
}
