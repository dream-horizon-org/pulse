import {
  useRef,
  useState,
  useLayoutEffect,
  useEffect,
  useCallback,
  type ReactNode,
} from "react";
import { Text } from "@mantine/core";
import {
  SESSION_LIST_CHIP_ROW_GAP_PX,
  SESSION_LIST_CHIP_ELLIPSIS_GAP_PX,
  SESSION_LIST_LABELS,
} from "../constants/sessionList.constants";

export interface FitMeasuredChipRowProps<T> {
  items: readonly T[];
  getKey: (item: T, index: number) => string;
  /** `lone` is true when this is the only visible chip (use full-width + label ellipsis if needed). */
  renderChip: (item: T, index: number, layout: { lone: boolean }) => ReactNode;
}

/**
 * Renders as many chips as fit in the parent width (measured off-screen), then a trailing "…"
 * if more items exist. Parent should wrap with Tooltip for the full list.
 */
export function FitMeasuredChipRow<T>({
  items,
  getKey,
  renderChip,
}: FitMeasuredChipRowProps<T>) {
  const containerRef = useRef<HTMLDivElement>(null);
  const measureRef = useRef<HTMLDivElement>(null);
  const ellipsisRef = useRef<HTMLSpanElement>(null);
  const [visibleCount, setVisibleCount] = useState(() => items.length);

  const recompute = useCallback(() => {
    const container = containerRef.current;
    const measure = measureRef.current;
    if (items.length === 0) {
      setVisibleCount(0);
      return;
    }
    if (!container || !measure) {
      setVisibleCount(items.length);
      return;
    }

    const cw = container.clientWidth;
    if (cw <= 0) {
      setVisibleCount(items.length);
      return;
    }

    const chipEls = Array.from(measure.children).slice(
      0,
      items.length,
    ) as HTMLElement[];
    const widths = chipEls.map((el) => el.getBoundingClientRect().width);
    if (
      chipEls.length !== items.length ||
      widths.some((w) => !Number.isFinite(w) || w <= 0)
    ) {
      setVisibleCount(items.length);
      return;
    }

    const ellipsisW = ellipsisRef.current?.getBoundingClientRect().width ?? 14;

    let best = 0;
    for (let k = items.length; k >= 0; k--) {
      let sum = 0;
      for (let i = 0; i < k; i++) {
        sum += widths[i];
        if (i > 0) {
          sum += SESSION_LIST_CHIP_ROW_GAP_PX;
        }
      }
      if (k < items.length) {
        sum += SESSION_LIST_CHIP_ELLIPSIS_GAP_PX + ellipsisW;
      }
      if (sum <= cw) {
        best = k;
        break;
      }
    }

    if (items.length > 0 && best === 0) {
      best = 1;
    }

    setVisibleCount((prev) => (prev === best ? prev : best));
  }, [items]);

  useLayoutEffect(() => {
    recompute();
    const id = requestAnimationFrame(recompute);
    return () => cancelAnimationFrame(id);
  }, [recompute]);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) {
      return;
    }
    const ro = new ResizeObserver(() => recompute());
    ro.observe(el);
    return () => ro.disconnect();
  }, [recompute]);

  if (items.length === 0) {
    return null;
  }

  const loneVisible = visibleCount === 1;

  return (
    <div
      style={{
        position: "relative",
        width: "100%",
        minWidth: 0,
      }}
    >
      <div
        ref={measureRef}
        aria-hidden
        style={{
          position: "absolute",
          left: -99999,
          top: 0,
          display: "flex",
          flexWrap: "nowrap",
          alignItems: "center",
          gap: SESSION_LIST_CHIP_ROW_GAP_PX,
          visibility: "hidden",
          pointerEvents: "none",
          whiteSpace: "nowrap",
        }}
      >
        {items.map((item, index) => (
          <div key={getKey(item, index)} style={{ flexShrink: 0 }}>
            {renderChip(item, index, { lone: false })}
          </div>
        ))}
        <Text
          ref={ellipsisRef}
          component="span"
          size="sm"
          c="dimmed"
          style={{ flexShrink: 0 }}
        >
          {SESSION_LIST_LABELS.truncationEllipsis}
        </Text>
      </div>

      <div
        ref={containerRef}
        style={{
          display: "flex",
          flexWrap: "nowrap",
          alignItems: "center",
          gap: SESSION_LIST_CHIP_ELLIPSIS_GAP_PX,
          minWidth: 0,
          width: "100%",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            display: "flex",
            flexWrap: "nowrap",
            flex: 1,
            minWidth: 0,
            gap: SESSION_LIST_CHIP_ROW_GAP_PX,
            overflow: "hidden",
            alignItems: "center",
          }}
        >
          {items.slice(0, visibleCount).map((item, index) => (
            <div
              key={getKey(item, index)}
              style={
                loneVisible
                  ? {
                      flexShrink: 1,
                      minWidth: 0,
                      maxWidth: "100%",
                      overflow: "hidden",
                    }
                  : { flexShrink: 0 }
              }
            >
              {renderChip(item, index, { lone: loneVisible })}
            </div>
          ))}
        </div>
        {visibleCount < items.length ? (
          <Text component="span" size="sm" c="dimmed" style={{ flexShrink: 0 }}>
            {SESSION_LIST_LABELS.truncationEllipsis}
          </Text>
        ) : null}
      </div>
    </div>
  );
}
