export interface PageHeaderProps {
  title: string;
  subtitle?: string;
  count?: number;
  countLabel?: string;
  onBack?: () => void;
  actions?: React.ReactNode;
}
