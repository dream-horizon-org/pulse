/** Data attached to each React Flow node in the journey graph. */
export interface JourneyNodeData extends Record<string, unknown> {
  displayName: string;
  rawName: string;
  userCount: number;
  percentage: number;
  isExpandable: boolean;
  isExpanded: boolean;
  isExit: boolean;
  isOther: boolean;
  onToggleExpand: (rawName: string) => void;
}

/** Data attached to each React Flow edge in the journey graph. */
export interface JourneyEdgeData extends Record<string, unknown> {
  userCount: number;
  percentage: number;
  sourceDisplayName: string;
  targetDisplayName: string;
}
