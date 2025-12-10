import classes from "./ScreenList.module.css";

import { Box, Group, ScrollArea, TextInput } from "@mantine/core";

import { ROUTES } from "../../constants";
import { useNavigate, useSearchParams } from "react-router-dom";
import { ChangeEvent, useEffect, useMemo, useRef, useState } from "react";
import { useGetScreenNames } from "../../hooks/useGetScreenNames";
import { useGetScreenDetails } from "../../hooks/useGetScreenDetails";
import { getDateFilterDetails } from "./utils";
import { ScreenCard } from "./components/ScreenCard";
import { ScreenCardSkeleton } from "./components/ScreenCardSkeleton";
import { filtersToQueryString } from "../../helpers/filtersToQueryString";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { LoaderWithMessage } from "../../components/LoaderWithMessage";
import { SCREEN_LISTING_PAGE_CONSTANTS } from "./ScreenList.constants";
import { useAnalytics } from "../../hooks/useAnalytics";

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

  useEffect(() => {
    setSearchParams(
      filtersToQueryString({
        ...Object.fromEntries(searchParams.entries()),
        screenName: searchStr,
      }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchStr]);

  // Get date filter details and memoize to prevent infinite loops
  const { startTime: startTimeStr, endTime: endTimeStr } = useMemo(
    () => getDateFilterDetails(),
    [],
  );

  // Convert date strings to ISO format for API
  const startTime = useMemo(() => {
    return startTimeStr;
  }, [startTimeStr]);

  const endTime = useMemo(() => {
    return endTimeStr;
  }, [endTimeStr]);

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
        <Box className={classes.loader}>
          <LoaderWithMessage loadingMessage="Loading screens..." />
        </Box>
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
                startTime={startTime}
                endTime={endTime}
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
        </Group>
      </Box>

      {/* Content Section */}
      {renderContent()}
    </Box>
  );
}

export default ScreenList;
