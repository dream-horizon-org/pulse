import {
  IconHome,
  IconListDetails,
  IconFileText,
  IconGridDots,
  IconArrowRight,
} from "@tabler/icons-react";
import { ScreensHealthProps } from "./ScreensHealth.interface";
import classes from "./ScreensHealth.module.css";
import { ScreenCard } from "../../../ScreenList/components/ScreenCard";
import { Button } from "@mantine/core";
import { useMemo } from "react";
import dayjs from "dayjs";
import { useGetScreensHealthData } from "../../../../hooks/useGetScreensHealthData";
import { SkeletonLoader } from "../../../../components/Skeletons";

// Skeleton card that matches the ScreenCard layout
function ScreenCardSkeleton() {
  return (
    <div className={classes.skeletonCard}>
      <div className={classes.skeletonMockup}>
        <SkeletonLoader height={56} width={56} radius="md" />
        <SkeletonLoader height={14} width="70%" radius="sm" />
      </div>
      <div className={classes.skeletonMetrics}>
        <div className={classes.skeletonMetricItem}>
          <SkeletonLoader height={8} width="60%" radius="sm" />
          <SkeletonLoader height={16} width="40%" radius="sm" />
        </div>
        <div className={classes.skeletonMetricItem}>
          <SkeletonLoader height={8} width="60%" radius="sm" />
          <SkeletonLoader height={16} width="40%" radius="sm" />
        </div>
        <div className={classes.skeletonMetricItem}>
          <SkeletonLoader height={8} width="60%" radius="sm" />
          <SkeletonLoader height={16} width="40%" radius="sm" />
        </div>
        <div className={classes.skeletonMetricItem}>
          <SkeletonLoader height={8} width="60%" radius="sm" />
          <SkeletonLoader height={16} width="40%" radius="sm" />
        </div>
      </div>
    </div>
  );
}

export function ScreensHealth({
  startTime,
  endTime,
  onViewAll,
  onCardClick,
}: ScreensHealthProps) {
  // Calculate date range - use provided time range or default to last 7 days
  const { startDate, endDate } = useMemo(() => {
    if (startTime && endTime) {
      return {
        startDate: dayjs.utc(startTime).toISOString(),
        endDate: dayjs.utc(endTime).toISOString(),
      };
    }
    const end = dayjs().utc().endOf("day");
    const start = end.subtract(6, "days").startOf("day");
    return {
      startDate: start.toISOString(),
      endDate: end.toISOString(),
    };
  }, [startTime, endTime]);

  const { data: screensData, isLoading } = useGetScreensHealthData({
    startTime: startDate,
    endTime: endDate,
    limit: 5,
  });

  const getScreenIcon = (screenType: string) => {
    switch (screenType) {
      case "home":
        return IconHome;
      case "detail":
        return IconListDetails;
      case "form":
        return IconFileText;
      case "list":
        return IconGridDots;
      default:
        return IconHome;
    }
  };

  return (
    <div>
      <div className={classes.headerContainer}>
        <h2 className={classes.sectionTitle}>Screen Health</h2>
        <Button
          variant="subtle"
          rightSection={<IconArrowRight size={16} />}
          onClick={onViewAll}
          className={classes.viewAllButton}
        >
          View All
        </Button>
      </div>
      <div className={classes.screensGrid}>
        {isLoading ? (
          <>
            <ScreenCardSkeleton />
            <ScreenCardSkeleton />
            <ScreenCardSkeleton />
            <ScreenCardSkeleton />
            <ScreenCardSkeleton />
          </>
        ) : (
          screensData.map((screen, index) => {
            const Icon = getScreenIcon(screen.screenType);

            return (
              <ScreenCard
                key={index}
                screenName={screen.screenName}
                icon={Icon}
                staticAvgTimeSpent={screen.avgTimeSpent}
                staticCrashRate={screen.crashRate}
                staticLoadTime={screen.loadTime}
                staticUsers={screen.users}
                onClick={() => onCardClick(screen.screenName)}
              />
            );
          })
        )}
      </div>
    </div>
  );
}
