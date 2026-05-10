import { ScreenVital } from "../..";

export interface VitalsByScreenTableProps {
  data: ScreenVital[] | undefined;
  isLoading: boolean;
  error: Error | null;
}
