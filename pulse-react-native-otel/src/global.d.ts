declare global {
  interface PerformanceLike {
    now(): number;
  }
  var performance: PerformanceLike | undefined;
}

export {};
