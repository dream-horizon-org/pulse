export interface VitalCardProps {
  name: string;
  p75: number;
  goodPct: number;
  needsImprovementPct: number;
  poorPct: number;
  isSelected?: boolean;
  onSelect?: () => void;
}
