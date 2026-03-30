import { useEffect, useLayoutEffect, useRef, useState } from "react";

/** Matches `SessionReplayDetail.module.css` @media (max-width: 900px) stacked layout */
const WIDE_SPLIT_MIN_PX = 901;

/**
 * Measures the player (left) column and exposes height so the timeline column can match it.
 * Disabled below `WIDE_SPLIT_MIN_PX` viewport width (stacked mobile layout).
 */
export function usePlayerRightPanelHeight() {
  const playerLeftRef = useRef<HTMLDivElement>(null);
  const [wideSplit, setWideSplit] = useState(false);
  const [heightPx, setHeightPx] = useState<number | null>(null);

  useEffect(() => {
    const mq = window.matchMedia(`(min-width: ${WIDE_SPLIT_MIN_PX}px)`);
    const onChange = () => setWideSplit(mq.matches);
    onChange();
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);

  useLayoutEffect(() => {
    if (!wideSplit) {
      setHeightPx(null);
      return;
    }
    const el = playerLeftRef.current;
    if (!el) return;
    const h = el.getBoundingClientRect().height;
    setHeightPx(h > 0 ? Math.round(h) : null);
  }, [wideSplit]);

  useEffect(() => {
    if (!wideSplit) return;
    const el = playerLeftRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => {
      const h = el.getBoundingClientRect().height;
      setHeightPx(h > 0 ? Math.round(h) : null);
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, [wideSplit]);

  const syncHeightPx =
    wideSplit && heightPx != null && heightPx > 0 ? heightPx : undefined;

  return { playerLeftRef, syncHeightPx };
}
