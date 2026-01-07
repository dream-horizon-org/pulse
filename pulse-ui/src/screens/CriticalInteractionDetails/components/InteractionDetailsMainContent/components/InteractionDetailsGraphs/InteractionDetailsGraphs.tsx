import { InteractionDetailsMainContentProps } from "../../InteractionDetailsMainContentProps.interface";
import { UserCategorisationGraph } from "../UserCategorisationGraph/UserCategorisationGraph";
import { ApdexWithLatencyGraph } from "../ApdexWithLatencyGraph";
import { ErrorMetricsGraph } from "../ErrorMetricsGraph";
import classes from "./InteractionDetailsGraphs.module.css";
import { useGetInteractionDetailsGraphs } from "../../../../../../hooks/useGetInteractionDetailsGraphs";
import { ErrorAndEmptyStateWithNotification } from "../ErrorAndEmptyStateWithNotification";
import { GraphCardSkeleton } from "../../../../../../components/Skeletons";

export function InteractionDetailsGraphs({
  ...detailsAndFilters
}: InteractionDetailsMainContentProps) {
  const {
    graphData,
    metrics,
    isLoading,
    isError,
  } = useGetInteractionDetailsGraphs({
    interactionName: detailsAndFilters?.jobDetails?.name,
    startTime: detailsAndFilters.startTime || "",
    endTime: detailsAndFilters.endTime || "",
    enabled: true,
    dashboardFilters: detailsAndFilters?.dashboardFilters,
  });

  const isHorizontal = detailsAndFilters?.orientation === "horizontal";

  if (isLoading) {
    return (
      <div className={`${classes.graphsMainContainer} ${isHorizontal ? classes.horizontal : classes.vertical}`}>
        <div className={classes.graphcolumn}>
          <GraphCardSkeleton chartHeight={isHorizontal ? 180 : 200} metricsCount={3} />
        </div>
        <div className={classes.graphcolumn}>
          <GraphCardSkeleton chartHeight={isHorizontal ? 180 : 200} metricsCount={3} />
        </div>
        <div className={classes.graphcolumn}>
          <GraphCardSkeleton chartHeight={isHorizontal ? 180 : 200} metricsCount={3} />
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <ErrorAndEmptyStateWithNotification
        message="Error fetching interaction details graphs"
        errorDetails="Unknown error"
        isError={true}
      />
    );
  }

  return (
    <div className={`${classes.graphsMainContainer} ${isHorizontal ? classes.horizontal : classes.vertical}`}>
      <div className={`${classes.graphcolumn}`}>
        <ApdexWithLatencyGraph
          {...detailsAndFilters}
          graphData={graphData}
          metrics={metrics}
        />
      </div>
      <div className={`${classes.graphcolumn}`}>
        <ErrorMetricsGraph
          {...detailsAndFilters}
          graphData={graphData}
          metrics={metrics}
        />
      </div>
      <div className={`${classes.graphcolumn}`}>
        <UserCategorisationGraph
          {...detailsAndFilters}
          graphData={graphData}
          metrics={metrics}
          height={isHorizontal ? 223 : 250}
        />
      </div>
    </div>
  );
}
