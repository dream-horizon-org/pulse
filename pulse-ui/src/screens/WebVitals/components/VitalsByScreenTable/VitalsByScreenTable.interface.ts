import type { ScreenVitalWire } from "../../WebVitalsWire.types";

export interface VitalsByScreenTableProps {
  /** Metric shown in the table (must match summary selection for rating thresholds). */
  vitalName: string;
  data: ReadonlyArray<ScreenVitalWire> | undefined;
  isLoading: boolean;
  error: Error | null;
}
