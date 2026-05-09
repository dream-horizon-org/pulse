/**
 * Android `ClickEventBuffer` parity — rage-click clustering + delayed individual emits.
 * @see pulse-android-otel/instrumentation/click-common/.../ClickEventBuffer.kt
 */

/** Monotonic ms — rage timing only (Android `elapsedRealtime` analogue). */
export type MonotonicClock = () => number;

export interface PendingClick {
  xPx: number;
  yPx: number;
  timestampMs: number;
  tapEpochMs: number;
  hasTarget: boolean;
  widgetName?: string;
  widgetId?: string;
  clickContext?: string;
  viewportWidthPx: number;
  viewportHeightPx: number;
}

export interface RageEvent {
  count: number;
  hasTarget: boolean;
  xPx: number;
  yPx: number;
  tapEpochMs: number;
  widgetName?: string;
  widgetId?: string;
  clickContext?: string;
  viewportWidthPx: number;
  viewportHeightPx: number;
}

export interface RageConfigResolved {
  timeWindowMs: number;
  threshold: number;
  radiusDp: number;
}

/** Android {@code RageConfig} defaults. */
export const DEFAULT_RAGE_CONFIG: RageConfigResolved = {
  timeWindowMs: 2000,
  threshold: 3,
  radiusDp: 50,
};

const MAX_ACTIVE_CLUSTERS = 5;

type RageCluster = {
  rage: RageEvent;
  lastTapTimeMs: number;
  timeoutId: ReturnType<typeof setTimeout>;
};

export interface ClickEventBufferOptions {
  densityScale: number;
  rageConfig: RageConfigResolved;
  onRage: (rage: RageEvent) => void;
  onEmit: (click: PendingClick) => void;
  /** Injectable monotonic clock (tests). Default {@code performance.now}. */
  monotonicNow?: MonotonicClock;
}

/**
 * UI-thread-equivalent buffer (call only from the click / flush path — no background workers).
 */
export class ClickEventBuffer {
  private readonly rageConfig: RageConfigResolved;
  private readonly radiusPxSquared: number;
  private readonly onRage: (rage: RageEvent) => void;
  private readonly onEmit: (click: PendingClick) => void;
  private readonly nowMono: MonotonicClock;
  private readonly buffer: PendingClick[] = [];
  private readonly activeRageClusters: RageCluster[] = [];
  private disposed = false;

  constructor(opts: ClickEventBufferOptions) {
    const effectiveDensity = opts.densityScale > 0 ? opts.densityScale : 1;
    const radiusPx = opts.rageConfig.radiusDp * effectiveDensity;
    this.radiusPxSquared = radiusPx * radiusPx;
    this.rageConfig = opts.rageConfig;
    this.onRage = opts.onRage;
    this.onEmit = opts.onEmit;
    this.nowMono =
      opts.monotonicNow ??
      (() =>
        typeof performance !== "undefined" &&
        typeof performance.now === "function"
          ? performance.now()
          : Date.now());

    if (this.rageConfig.threshold <= 0) {
      throw new Error(
        `rage threshold must be > 0, got ${this.rageConfig.threshold}`,
      );
    }
    if (this.rageConfig.timeWindowMs <= 0) {
      throw new Error(
        `rage timeWindowMs must be > 0, got ${this.rageConfig.timeWindowMs}`,
      );
    }
  }

  record(click: PendingClick): void {
    if (this.disposed) return;
    const nowMs = click.timestampMs;
    this.emitExpiredClusters(nowMs);

    const matchingCluster = this.findNearestClusterWithinRadius(click);
    if (matchingCluster !== undefined) {
      this.extendCluster(matchingCluster, click);
      return;
    }

    this.processNormal(click);
  }

  /** Emit all rage clusters and buffered individual clicks (Android {@code flush}). */
  flush(): void {
    if (this.disposed) return;
    for (const cluster of [...this.activeRageClusters]) {
      this.cancelDelayed(cluster.timeoutId);
      this.removeCluster(cluster);
      this.onRage(cluster.rage);
    }
    this.activeRageClusters.length = 0;
    while (this.buffer.length > 0) {
      const c = this.buffer.shift();
      if (c) this.onEmit(c);
    }
  }

  /**
   * Flush pending rage + individual clicks, cancel timers — call on instrumentation uninstall.
   * Matches Android {@code flush()} (do not drop in-flight rage clusters).
   */
  dispose(): void {
    if (this.disposed) return;
    this.flush();
    this.disposed = true;
  }

  private cancelDelayed(id: ReturnType<typeof setTimeout>): void {
    clearTimeout(id);
  }

  private postDelayed(
    fn: () => void,
    ms: number,
  ): ReturnType<typeof setTimeout> {
    return setTimeout(fn, ms);
  }

  private removeCluster(cluster: RageCluster): void {
    const i = this.activeRageClusters.indexOf(cluster);
    if (i >= 0) this.activeRageClusters.splice(i, 1);
  }

  private findNearestClusterWithinRadius(
    click: PendingClick,
  ): RageCluster | undefined {
    const candidates = this.activeRageClusters.filter((c) =>
      this.withinRadius(click.xPx, click.yPx, c.rage.xPx, c.rage.yPx),
    );
    if (candidates.length === 0) return undefined;
    let best = candidates[0]!;
    let bestD = this.distanceSquared(
      click.xPx,
      click.yPx,
      best.rage.xPx,
      best.rage.yPx,
    );
    for (let i = 1; i < candidates.length; i++) {
      const c = candidates[i]!;
      const d = this.distanceSquared(
        click.xPx,
        click.yPx,
        c.rage.xPx,
        c.rage.yPx,
      );
      if (d < bestD) {
        bestD = d;
        best = c;
      }
    }
    return best;
  }

  private extendCluster(cluster: RageCluster, click: PendingClick): void {
    cluster.lastTapTimeMs = click.timestampMs;
    cluster.rage = {
      ...cluster.rage,
      count: cluster.rage.count + 1,
      tapEpochMs: click.tapEpochMs,
    };
    this.cancelDelayed(cluster.timeoutId);
    cluster.timeoutId = this.postDelayed(() => {
      this.removeCluster(cluster);
      this.onRage(cluster.rage);
    }, this.rageConfig.timeWindowMs);
  }

  private processNormal(click: PendingClick): void {
    this.evictStale(click.timestampMs);
    this.buffer.push(click);

    const nearbyCount = this.buffer.filter((p) =>
      this.withinRadius(p.xPx, p.yPx, click.xPx, click.yPx),
    ).length;

    if (nearbyCount >= this.rageConfig.threshold) {
      const rage: RageEvent = {
        count: nearbyCount,
        hasTarget: click.hasTarget,
        xPx: click.xPx,
        yPx: click.yPx,
        tapEpochMs: click.tapEpochMs,
        widgetName: click.widgetName,
        widgetId: click.widgetId,
        clickContext: click.clickContext,
        viewportWidthPx: click.viewportWidthPx,
        viewportHeightPx: click.viewportHeightPx,
      };

      for (let i = this.buffer.length - 1; i >= 0; i--) {
        const p = this.buffer[i]!;
        if (this.withinRadius(p.xPx, p.yPx, click.xPx, click.yPx)) {
          this.buffer.splice(i, 1);
        }
      }

      if (this.activeRageClusters.length >= MAX_ACTIVE_CLUSTERS) {
        const oldest = this.activeRageClusters.reduce((a, b) =>
          a.lastTapTimeMs <= b.lastTapTimeMs ? a : b,
        );
        this.cancelDelayed(oldest.timeoutId);
        this.removeCluster(oldest);
        this.onRage(oldest.rage);
      }

      const cluster: RageCluster = {
        rage,
        lastTapTimeMs: click.timestampMs,
        timeoutId: 0 as unknown as ReturnType<typeof setTimeout>,
      };
      cluster.timeoutId = this.postDelayed(() => {
        this.removeCluster(cluster);
        this.onRage(cluster.rage);
      }, this.rageConfig.timeWindowMs);
      this.activeRageClusters.push(cluster);
    }
  }

  private emitExpiredClusters(nowMs: number): void {
    const win = this.rageConfig.timeWindowMs;
    for (let i = this.activeRageClusters.length - 1; i >= 0; i--) {
      const cluster = this.activeRageClusters[i]!;
      if (nowMs - cluster.lastTapTimeMs > win) {
        this.cancelDelayed(cluster.timeoutId);
        this.activeRageClusters.splice(i, 1);
        this.onRage(cluster.rage);
      }
    }
  }

  private evictStale(nowMs: number): void {
    const cutoff = nowMs - this.rageConfig.timeWindowMs;
    while (this.buffer.length > 0 && this.buffer[0]!.timestampMs < cutoff) {
      const first = this.buffer.shift()!;
      this.onEmit(first);
    }
  }

  private withinRadius(
    x1: number,
    y1: number,
    x2: number,
    y2: number,
  ): boolean {
    const dx = x1 - x2;
    const dy = y1 - y2;
    return dx * dx + dy * dy <= this.radiusPxSquared;
  }

  private distanceSquared(
    x1: number,
    y1: number,
    x2: number,
    y2: number,
  ): number {
    const dx = x1 - x2;
    const dy = y1 - y2;
    return dx * dx + dy * dy;
  }
}

export function resolveClickRageConfig(raw?: {
  enabled?: boolean;
  timeWindowMs?: number;
  threshold?: number;
  radiusDp?: number;
}): RageConfigResolved | null {
  if (raw?.enabled === false) {
    return null;
  }
  return {
    timeWindowMs: raw?.timeWindowMs ?? DEFAULT_RAGE_CONFIG.timeWindowMs,
    threshold: raw?.threshold ?? DEFAULT_RAGE_CONFIG.threshold,
    radiusDp: raw?.radiusDp ?? DEFAULT_RAGE_CONFIG.radiusDp,
  };
}
