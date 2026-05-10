export const WEB_VITAL_THRESHOLDS: Record<string, { good: number; needsImprovement: number }> = {
  LCP: {
    good: 2500,
    needsImprovement: 4000,
  },
  INP: {
    good: 200,
    needsImprovement: 500,
  },
  CLS: {
    good: 0.1,
    needsImprovement: 0.25,
  },
  FCP: {
    good: 1800,
    needsImprovement: 3000,
  },
  FID: {
    good: 100,
    needsImprovement: 300,
  },
  TTFB: {
    good: 800,
    needsImprovement: 1800,
  },
};

export type VitalRating = "good" | "needsImprovement" | "poor";

export const getVitalRating = (p75: number, vitalName: string): VitalRating => {
  const threshold = WEB_VITAL_THRESHOLDS[vitalName];
  if (!threshold) {
    return "poor";
  }

  if (p75 <= threshold.good) {
    return "good";
  }

  if (p75 <= threshold.needsImprovement) {
    return "needsImprovement";
  }

  return "poor";
};
