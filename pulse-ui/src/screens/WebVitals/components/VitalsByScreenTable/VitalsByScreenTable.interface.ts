import type { ScreenVitalWire } from "../../WebVitalsWire.types";

export interface VitalsByScreenTableProps {
  data: ReadonlyArray<ScreenVitalWire> | undefined;
  isLoading: boolean;
  error: Error | null;
}
