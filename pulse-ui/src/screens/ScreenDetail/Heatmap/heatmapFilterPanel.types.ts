import type { ReactNode } from "react";
import type { HeatmapLocalFilters } from "./heatmapLocalFilters";
import type { HeatmapFocusLens, HeatmapSignal } from "./heatmapPanelUtils";

export type HeatmapFilterPanelVariant = "full" | "dataOnly";

/** `compareColumn`: label, time + filters row, audience pills row (compare mode). */
export type HeatmapDataOnlyLayout = "inline" | "compareColumn";

export interface HeatmapFilterPanelProps {
  variant?: HeatmapFilterPanelVariant;
  value?: HeatmapLocalFilters;
  onChange?: (next: HeatmapLocalFilters) => void;
  onResetToPage?: () => void;
  /** Whole-panel match (time + audience) vs page. */
  matchesPage?: boolean;
  sectionLabel?: string;
  /** Used when `variant="dataOnly"`; compare uses `compareColumn`. */
  dataOnlyLayout?: HeatmapDataOnlyLayout;
  /** Rendered on row 2 right (e.g. Compare screens). */
  toolbarEnd?: ReactNode;
  signal?: HeatmapSignal;
  onSignalChange?: (s: HeatmapSignal) => void;
  focusLens?: HeatmapFocusLens;
  onFocusLensChange?: (l: HeatmapFocusLens) => void;
  /** When false, only Heat map is offered (no `interaction_map` on the current response). */
  showInteractionMapOption?: boolean;
}

export interface HeatmapMapViewControlsProps {
  signal: HeatmapSignal;
  onSignalChange?: (s: HeatmapSignal) => void;
  focusLens: HeatmapFocusLens;
  onFocusLensChange?: (l: HeatmapFocusLens) => void;
  showInteractionMapOption?: boolean;
}

export interface HeatmapTimeRangePopoverBodyProps {
  opened: boolean;
  value: HeatmapLocalFilters;
  onChange: (next: HeatmapLocalFilters) => void;
}

export interface HeatmapAudienceFilterFormProps {
  value: HeatmapLocalFilters;
  onChange: (next: HeatmapLocalFilters) => void;
  platformSuggestions: string[];
  appVersionSuggestions: string[];
  regionSuggestions: string[];
}

export interface HeatmapTimeFilterPopoverProps {
  opened: boolean;
  onOpenChange: (opened: boolean) => void;
  timeButtonLabel: string;
  dropdownWidth?: number;
  children: ReactNode;
}

export interface HeatmapAudienceFilterPopoverProps {
  opened: boolean;
  onOpenChange: (opened: boolean) => void;
  audienceActiveCount: number;
  dropdownWidth?: number;
  onResetToPage?: () => void;
  audienceHint: string;
  children: ReactNode;
}
