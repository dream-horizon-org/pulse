import { MetricItemProps } from "../MetricItem/MetricItem.interface";

export interface MetricGroupProps {
  title: string;
  description?: string;
  metrics: MetricItemProps[];
  icon?: React.ElementType;
  iconColor?: string;
}
