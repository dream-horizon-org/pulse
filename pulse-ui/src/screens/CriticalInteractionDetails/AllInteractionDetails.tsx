import { Title } from "@mantine/core";
import classes from "./CriticalInteractionDetails.module.css";
import { CRITICAL_INTERACTION_DETAILS_PAGE_CONSTANTS } from "../../constants";
import { InteractionDetailsFilters } from "./components/InteractionDetailsFilters";
import { InteractionDetailsMainContent } from "./components/InteractionDetailsMainContent";
import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { useFilterStore } from "../../stores/useFilterStore";

export function AllInteractionDetails() {
  const [searchParams] = useSearchParams();

  const {
    filterValues,
    startTime,
    endTime,
    quickTimeRangeString,
    quickTimeRangeFilterIndex,
    initializeFromUrlParams,
  } = useFilterStore();

  const title =
    CRITICAL_INTERACTION_DETAILS_PAGE_CONSTANTS.ALL_INTERACTIONS_TITLE;

  useEffect(() => {
    initializeFromUrlParams(searchParams);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return (
    <div className={classes.criticalInteractionDetailsContainer}>
      <div className={classes.criticalInteractionDetailsHeader}>
        <Title order={4}>{title}</Title>
      </div>
      {quickTimeRangeString !== null &&
        quickTimeRangeFilterIndex !== null &&
        filterValues && <InteractionDetailsFilters />}
      {filterValues && startTime && endTime && (
        <InteractionDetailsMainContent
          dashboardFilters={filterValues}
          startTime={startTime}
          endTime={endTime}
        />
      )}
    </div>
  );
}
