export interface MetricItemProps {
  title: string;
  value: number;
  trend: string;
  color: string;
  icon: React.ElementType;
  onClick?: () => void;
}
