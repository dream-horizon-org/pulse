import classes from "./ScreenList.module.css";

import { Box, Group, ScrollArea, TextInput } from "@mantine/core";

import {
  ROUTES,
  DEFAULT_QUICK_TIME_FILTER,
  DEFAULT_QUICK_TIME_FILTER_INDEX,
} from "../../constants";
import { useNavigate, useSearchParams } from "react-router-dom";
import { ChangeEvent, useEffect, useRef, useState } from "react";
import { useGetScreenNames } from "../../hooks/useGetScreenNames";
import { useGetScreenDetails } from "../../hooks/useGetScreenDetails";
import { ScreenCard } from "./components/ScreenCard";
import { ScreenCardSkeleton } from "./components/ScreenCardSkeleton";
import { filtersToQueryString } from "../../helpers/filtersToQueryString";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { SCREEN_LISTING_PAGE_CONSTANTS } from "./ScreenList.constants";
import { useAnalytics } from "../../hooks/useAnalytics";
import { CardSkeleton } from "../../components/Skeletons";
import DateTimeRangePicker from "../CriticalInteractionDetails/components/DateTimeRangePicker/DateTimeRangePicker";
import { StartEndDateTimeType } from "../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";
import { useFilterStore } from "../../stores/useFilterStore";
import { getStartAndEndDateTimeString } from "../../utils/DateUtil";

export function ScreenList() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { trackClick, trackSearch } = useAnalytics("ScreenList");
  const searchFields = Object.fromEntries(searchParams.entries());
  const [searchStr, setSearchStr] = useState<string>(
    searchFields?.screenName || "",
  );

  // Debounced search string for API calls
  const [debouncedSearchStr, setDebouncedSearchStr] = useState<string>(
    searchFields?.screenName || "",
  );

  const scrollContainerRef = useRef<HTMLDivElement | null>(null);

  // Use filter store for time range state
  const {
    startTime: storeStartTime,
    endTime: storeEndTime,
    quickTimeRangeString,
    quickTimeRangeFilterIndex,
    handleTimeFilterChange: storeHandleTimeFilterChange,
    initializeFromUrlParams,
  } = useFilterStore();

  // Initialize default time range (Last 24 hours)
  const getDefaultTimeRange = () => {
    return getStartAndEndDateTimeString(DEFAULT_QUICK_TIME_FILTER, 2);
  };

  // Initialize filter store from URL params
  useEffect(() => {
    initializeFromUrlParams(searchParams);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  // Use store values for time range
  const startTime = storeStartTime || getDefaultTimeRange().startDate;
  const endTime = storeEndTime || getDefaultTimeRange().endDate;

  const handleTimeFilterChange = (value: StartEndDateTimeType) => {
    storeHandleTimeFilterChange(value);
  };

  useEffect(() => {
    setSearchParams(
      filtersToQueryString({
        ...Object.fromEntries(searchParams.entries()),
        screenName: searchStr,
      }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchStr]);

  // First hook: Get screen names (for skeleton cards)
  // Use debounced search string for API calls
  const {
    screenNames,
    isLoading: isLoadingScreenNames,
    isError: isErrorScreenNames,
  } = useGetScreenNames({
    startTime,
    endTime,
    searchStr: debouncedSearchStr,
    enabled: true,
  });

  // Second hook: Get screen details (for populating cards)
  const {
    screensMap,
    isLoading: isLoadingScreenDetails,
    isError: isErrorScreenDetails,
  } = useGetScreenDetails({
    screenNames,
    startTime,
    endTime,
    enabled: screenNames.length > 0,
  });

  const isLoading = isLoadingScreenNames || isLoadingScreenDetails;
  const isError = isErrorScreenNames || isErrorScreenDetails;
  const totalRecords = screenNames.length;

  // Update debounced search string after user stops typing
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearchStr(searchStr);
      if (searchStr.trim()) {
        trackSearch(searchStr);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [searchStr, trackSearch]);

  // Handle immediate input changes for UI responsiveness
  const handleSearchChange = (e: ChangeEvent<HTMLInputElement>) => {
    setSearchStr(e.target.value);
  };

  const renderContent = () => {
    if (isError) {
      return (
        <ErrorAndEmptyState
          classes={[classes.error]}
          message={SCREEN_LISTING_PAGE_CONSTANTS.ERROR_LOADING_SCREENS}
        />
      );
    }

    if (isLoadingScreenNames && screenNames.length === 0) {
      return (
        <ScrollArea
          viewportRef={scrollContainerRef}
          className={classes.scrollArea}
        >
          <Box className={classes.screenTableContainer}>
            {Array.from({ length: 8 }).map((_, index) => (
              <CardSkeleton
                key={index}
                height={220}
                showHeader
                contentRows={4}
              />
            ))}
          </Box>
        </ScrollArea>
      );
    }

    if (!isLoading && screenNames.length === 0) {
      return (
        <ErrorAndEmptyState
          classes={[classes.error]}
          message={SCREEN_LISTING_PAGE_CONSTANTS.NO_SCREENS_FOUND}
          description={SCREEN_LISTING_PAGE_CONSTANTS.NO_SCREENS_DESCRIPTION}
        />
      );
    }

    return (
      <ScrollArea
        viewportRef={scrollContainerRef}
        className={classes.scrollArea}
      >
        <Box className={classes.screenTableContainer}>
          {screenNames.map((screenName) => {
            const screenDetails = screensMap[screenName];
            const isLoadingDetails = isLoadingScreenDetails && !screenDetails;

            if (isLoadingDetails) {
              return (
                <ScreenCardSkeleton key={screenName} screenName={screenName} />
              );
            }

            return (
              <ScreenCard
                key={screenName}
                screenName={screenName}
                staticAvgTimeSpent={screenDetails?.avgTimeSpent}
                staticCrashRate={screenDetails?.errorRate}
                staticLoadTime={screenDetails?.loadTime}
                staticUsers={screenDetails?.users}
                onClick={() => {
                  trackClick(`Screen: ${screenName}`);
                  navigate(
                    `${ROUTES.SCREEN_DETAILS.basePath}/${encodeURIComponent(screenName)}`,
                  );
                }}
              />
            );
          })}
        </Box>
      </ScrollArea>
    );
  };

  return (
    <Box className={classes.pageContainer}>
      {/* Compact Header */}
      <Box className={classes.pageHeader}>
        <Box className={classes.titleSection}>
          <h1 className={classes.pageTitle}>
            {SCREEN_LISTING_PAGE_CONSTANTS.PAGE_TITLE}
          </h1>
          <span className={classes.screenCount}>
            {totalRecords} {totalRecords === 1 ? "Screen" : "Screens"}
          </span>
        </Box>
      </Box>

      {/* Search Section */}
      <Box className={classes.controlsSection}>
        <Group className={classes.searchBarContainer}>
          <TextInput
            className={classes.searchInput}
            placeholder={SCREEN_LISTING_PAGE_CONSTANTS.SEARCH_PLACEHOLDER}
            onChange={handleSearchChange}
            size="sm"
            value={searchStr}
          />
          <DateTimeRangePicker
            handleTimefilterChange={handleTimeFilterChange}
            selectedQuickTimeFilterIndex={
              quickTimeRangeFilterIndex !== null
                ? quickTimeRangeFilterIndex
                : DEFAULT_QUICK_TIME_FILTER_INDEX
            }
            defaultQuickTimeFilterIndex={DEFAULT_QUICK_TIME_FILTER_INDEX}
            defaultQuickTimeFilterString={
              quickTimeRangeString || DEFAULT_QUICK_TIME_FILTER
            }
            defaultEndTime={endTime}
            defaultStartTime={startTime}
          />
        </Group>
      </Box>

      {/* Content Section */}
      {renderContent()}
    </Box>
  );
}

export default ScreenList;
