export interface ScreenHealth {
  screenName: string;
  avgTimeSpent: number | undefined;
  crashRate: number;
  loadTime: number;
  users: number;
  screenType: "home" | "detail" | "form" | "list";
}

export interface ScreensHealthProps {
  startTime?: string;
  endTime?: string;
  onViewAll: () => void;
  onCardClick: (screenName: string) => void;
}
