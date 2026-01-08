import { create } from "zustand";
import { devtools } from "zustand/middleware";
import { CriticalInteractionDetailsFilterValues } from "../screens/CriticalInteractionDetails/CriticalInteractionDetails.interface";
import {
  getStartAndEndDateTimeString,
  getLocalStringFromUTCDateTimeValue,
} from "../utils/DateUtil";
import {
  CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS,
  DATE_FORMAT,
  DEFAULT_QUICK_TIME_FILTER_INDEX,
} from "../constants/Constants";
import { CriticalInteractionDetailsFilterOptionsResponse } from "../helpers/getCriticalInteractionDetailsFilterOptions/getCriticalInteractionDetailsFilterOptions.interface";
import { interactionDetailsfilterDefaultValues } from "../screens/CriticalInteractionDetails/components/InteractionDetailsFilters/InteractionDetailsFilters.interface";
import { StartEndDateTimeType } from "../screens/CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";
import { filtersToQueryString } from "../helpers/filtersToQueryString";
import dayjs from "dayjs";

const DEFAULT_FILTER_OPTIONS: CriticalInteractionDetailsFilterOptionsResponse =
  {
    PLATFORM: [],
    APP_VERSION: [],
    NETWORK_PROVIDER: [],
    STATE: [],
    OS_VERSION: [],
  };

interface FilterStore {
  // Time and filter state
  filterValues?: CriticalInteractionDetailsFilterValues;
  startTime: string;
  endTime: string;
  quickTimeRangeString: string | null;
  quickTimeRangeFilterIndex: number | null;

  // Filter UI state
  selectedFilters: string[];
  timeFilterOptions: StartEndDateTimeType;
  filterOptions: CriticalInteractionDetailsFilterOptionsResponse;

  // DateTimeRangePicker state
  dateTimePickerOpened: boolean;
  selectedTimeFilter: StartEndDateTimeType;
  activeQuickTimeFilter: number;
  
  // Pending/draft state for date picker (before Apply is clicked)
  pendingTimeFilter: StartEndDateTimeType;
  pendingQuickTimeFilter: number;

  // Actions
  setFilterValues: (values: CriticalInteractionDetailsFilterValues) => void;
  setQuickTimeRange: (timeRange: string | null, index: number | null) => void;
  handleFilterChange: (
    filterValues: CriticalInteractionDetailsFilterValues,
    startDateTime?: string,
    endDateTime?: string,
    quickTimeRange?: string,
  ) => void;
  initializeFromUrlParams: (searchParams: URLSearchParams) => void;
  // Filter UI actions
  handleOnChange: (filters: string[]) => void;
  initializeDefaultSelectedFilters: (
    filterValues?: CriticalInteractionDetailsFilterValues,
  ) => void;
  setFilterOptions: (
    options: CriticalInteractionDetailsFilterOptionsResponse,
  ) => void;
  handleTimeFilterChange: (options: StartEndDateTimeType) => void;
  initializeTimeFilterOptions: () => void;

  // DateTimeRangePicker actions
  setDateTimePickerOpened: (opened: boolean) => void;
  toggleDateTimePickerOpened: () => void;
  setSelectedTimeFilter: (filter: StartEndDateTimeType) => void;
  setActiveQuickTimeFilter: (index: number) => void;
  handleQuickTimeFilterChange: (value: number) => void;
  handleStartEndDateTimeChange: (value: StartEndDateTimeType) => void;
  handleDateTimeRefreshButton: (subtractMinutes?: number) => void;
  initializeDateTimePickerState: (
    selectedQuickTimeFilterIndex: number,
    quickTimeRangeString: string,
    startTime: string,
    endTime: string,
    subtractMinutes?: number,
  ) => void;

  handleDateTimeApply: (
    value: StartEndDateTimeType,
    searchParams: URLSearchParams,
    handleTimefilterChange: (value: StartEndDateTimeType) => void,
  ) => string;
  handleDateTimeReset: (
    defaultQuickTimeOptions: any[],
    defaultQuickTimeFilterIndex: number,
    subractMinutes: number,
    searchParams: URLSearchParams,
    handleTimefilterChange: (value: StartEndDateTimeType) => void,
  ) => string;
  getDateTimeDisplayText: () => string;
  handleDateTimeRefreshClick: (
    subractMinutes: number,
    handleTimefilterChange: (value: StartEndDateTimeType) => void,
  ) => void;
  
  // Sync pending state with applied state (called when popover opens)
  syncPendingWithApplied: () => void;
}

// Use literal value 7 to avoid circular dependency issues with DEFAULT_QUICK_TIME_FILTER_INDEX
// This matches the value of DEFAULT_QUICK_TIME_FILTER_INDEX (index of LAST_24_HOURS)
const INITIAL_QUICK_TIME_FILTER_INDEX = 7;

const initialState = {
  filterValues: undefined,
  startTime: "",
  endTime: "",
  quickTimeRangeString: null,
  quickTimeRangeFilterIndex: null,
  selectedFilters: [...interactionDetailsfilterDefaultValues],
  timeFilterOptions: { startDate: "", endDate: "" },
  filterOptions: DEFAULT_FILTER_OPTIONS,
  dateTimePickerOpened: false,
  selectedTimeFilter: { startDate: "", endDate: "" },
  activeQuickTimeFilter: INITIAL_QUICK_TIME_FILTER_INDEX,
  // Pending state for date picker draft selections
  pendingTimeFilter: { startDate: "", endDate: "" },
  pendingQuickTimeFilter: INITIAL_QUICK_TIME_FILTER_INDEX,
};

export const useFilterStore = create<FilterStore>()(
  devtools(
    (set, get) => ({
      ...initialState,

      // Simple setters
      setFilterValues: (values) => set({ filterValues: values }),
      setQuickTimeRange: (timeRange, index) =>
        set({
          quickTimeRangeString: timeRange,
          quickTimeRangeFilterIndex: index,
        }),

      // Complex actions
      handleFilterChange: (
        filterValues,
        startDateTime = "",
        endDateTime = "",
        quickTimeRange = "",
      ) => {
        set({
          filterValues,
          startTime: startDateTime,
          endTime: endDateTime,
          quickTimeRangeString: quickTimeRange,
        });
      },

      initializeFromUrlParams: (searchParams) => {
        const params = Object.fromEntries(searchParams.entries());
        const filterValues = {
          PLATFORM: params.PLATFORM || "",
          APP_VERSION: params.APP_VERSION || "",
          NETWORK_PROVIDER: params.NETWORK_PROVIDER || "",
          OS_VERSION: params.OS_VERSION || "",
          STATE: params.STATE || "",
        };
        const quickDateFilterIndex = params.quickDateFilter !== undefined 
          ? Number(params.quickDateFilter) 
          : DEFAULT_QUICK_TIME_FILTER_INDEX;
        
        if (quickDateFilterIndex !== -1) {
          const dateTimeFilter = getStartAndEndDateTimeString(
            CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS[quickDateFilterIndex].value,
            2,
          );

          set({
            quickTimeRangeString:
              CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS[quickDateFilterIndex].value,
            startTime: dateTimeFilter.startDate,
            endTime: dateTimeFilter.endDate,
            // Also set timeFilterOptions to keep in sync
            timeFilterOptions: {
              startDate: dateTimeFilter.startDate,
              endDate: dateTimeFilter.endDate,
            },
            quickTimeRangeFilterIndex: quickDateFilterIndex,
            activeQuickTimeFilter: quickDateFilterIndex,
            pendingQuickTimeFilter: quickDateFilterIndex,
            selectedTimeFilter: dateTimeFilter,
            pendingTimeFilter: dateTimeFilter,
            filterValues,
          });
        } else if (
          params.quickDateFilter &&
          Number(params.quickDateFilter) === -1
        ) {
          // URL contains UTC times (universal format for cross-timezone sharing)
          // Decode and clean URL encoding
          const rawStartDate = params.startDate || "";
          const rawEndDate = params.endDate || "";
          
          // Handle URL encoding: decode URI components and replace + with space
          let utcStartDate = rawStartDate.replace(/\+/g, " ");
          let utcEndDate = rawEndDate.replace(/\+/g, " ");
          
          // Try to decode if still encoded
          try {
            utcStartDate = decodeURIComponent(utcStartDate);
            utcEndDate = decodeURIComponent(utcEndDate);
          } catch {
            // Already decoded or invalid encoding, use as is
          }
          
          // Convert UTC to local for display (viewer sees their local time)
          const localStartDate = getLocalStringFromUTCDateTimeValue(utcStartDate);
          const localEndDate = getLocalStringFromUTCDateTimeValue(utcEndDate);
          
          const localTimeFilter = {
            startDate: localStartDate,
            endDate: localEndDate,
          };
          
          set({
            // Keep UTC times for query usage
            startTime: utcStartDate,
            endTime: utcEndDate,
            // Also set timeFilterOptions to prevent InteractionDetailsFilters from overwriting
            timeFilterOptions: {
              startDate: utcStartDate,
              endDate: utcEndDate,
            },
            quickTimeRangeFilterIndex: -1,
            activeQuickTimeFilter: -1,
            pendingQuickTimeFilter: -1,
            quickTimeRangeString: "",
            // Convert to local times for display
            selectedTimeFilter: localTimeFilter,
            pendingTimeFilter: localTimeFilter,
            filterValues,
          });
        } else {
          set({ filterValues });
        }
      },

      // Filter UI actions

      handleOnChange: (filters) => {
        set({ selectedFilters: filters });
      },

      initializeDefaultSelectedFilters: (filterValues) => {
        const currentFilterValues = filterValues || get().filterValues;
        let filterArr = [...interactionDetailsfilterDefaultValues];

        if (
          currentFilterValues?.NETWORK_PROVIDER &&
          !filterArr.includes("NETWORK_PROVIDER")
        ) {
          filterArr.push("NETWORK_PROVIDER");
        }

        if (currentFilterValues?.STATE && !filterArr.includes("STATE")) {
          filterArr.push("STATE");
        }

        set({ selectedFilters: filterArr });
      },

      setFilterOptions: (options) => set({ filterOptions: options }),

      handleTimeFilterChange: (options) => {
        const { filterValues, handleFilterChange } = get();
        // Update all time-related state
        set({ 
          timeFilterOptions: options,
          startTime: options.startDate || "",
          endTime: options.endDate || "",
        });

        if (filterValues) {
          handleFilterChange(filterValues, options.startDate, options.endDate);
        }
      },

      initializeTimeFilterOptions: () => {
        const {
          quickTimeRangeFilterIndex,
          quickTimeRangeString,
          startTime,
          endTime,
        } = get();

        const timeOptions =
          quickTimeRangeFilterIndex !== -1
            ? getStartAndEndDateTimeString(quickTimeRangeString || "", 2)
            : {
                startDate: startTime,
                endDate: endTime,
              };

        set({ timeFilterOptions: timeOptions });
      },

      // DateTimeRangePicker actions
      setDateTimePickerOpened: (opened) =>
        set({ dateTimePickerOpened: opened }),

      toggleDateTimePickerOpened: () => {
        const { dateTimePickerOpened } = get();
        set({ dateTimePickerOpened: !dateTimePickerOpened });
      },

      setSelectedTimeFilter: (filter) => set({ selectedTimeFilter: filter }),

      setActiveQuickTimeFilter: (index) =>
        set({ activeQuickTimeFilter: index }),

      handleQuickTimeFilterChange: (value) => {
        // Update pending state only - display will update on Apply
        set({ pendingQuickTimeFilter: value });
      },

      handleStartEndDateTimeChange: (value) => {
        // Update pending state only - display will update on Apply
        set({
          pendingTimeFilter: {
            startDate: value.startDate,
            endDate: value.endDate,
          },
        });
      },

      handleDateTimeRefreshButton: (subtractMinutes = 2) => {
        const { activeQuickTimeFilter } = get();
        const refreshedTimeFilter = getStartAndEndDateTimeString(
          CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS[
            activeQuickTimeFilter
          ].value,
          subtractMinutes,
        );

        // Update all time-related state to ensure components get the refreshed values
        set({ 
          selectedTimeFilter: refreshedTimeFilter,
          startTime: refreshedTimeFilter.startDate,
          endTime: refreshedTimeFilter.endDate,
          timeFilterOptions: refreshedTimeFilter,
        });
      },

      initializeDateTimePickerState: (
        selectedQuickTimeFilterIndex,
        quickTimeRangeString,
        startTimeArg,
        endTimeArg,
        subtractMinutes = 2,
      ) => {
        const currentState = get();
        
        // If store was already initialized with custom dates from URL (quickTimeRangeFilterIndex === -1),
        // don't overwrite with quick filter times even if selectedQuickTimeFilterIndex is 0
        // This handles the case where the prop hasn't updated yet but store has
        if (currentState.quickTimeRangeFilterIndex === -1) {
          // Store has custom dates from URL, don't overwrite
          return;
        }
        
        // If store already has valid times and quickTimeRangeString is empty,
        // don't re-initialize (would cause default 15-min range to be applied)
        if (currentState.startTime && currentState.endTime && !quickTimeRangeString) {
          return;
        }
        
        // IMPORTANT: If the store's activeQuickTimeFilter is different from what's being passed,
        // AND the store already has valid times, the store was just updated by handleDateTimeApply.
        // Don't overwrite with potentially stale prop values.
        if (currentState.startTime && currentState.endTime && 
            currentState.activeQuickTimeFilter !== selectedQuickTimeFilterIndex &&
            currentState.activeQuickTimeFilter >= 0) {
          // Store has more recent data from handleDateTimeApply, skip re-initialization
          return;
        }
        
        // For quick filters: generate fresh UTC times
        // For custom dates: preserve existing UTC times in store, only update display state
        if (selectedQuickTimeFilterIndex !== -1) {
          // Quick filter - generate fresh times
          const quickFilterTimes = getStartAndEndDateTimeString(
                quickTimeRangeString,
                subtractMinutes,
          );
          
          // Skip if times haven't changed
          if (currentState.startTime === quickFilterTimes.startDate && 
              currentState.endTime === quickFilterTimes.endDate) {
            return;
          }

          set({
            selectedTimeFilter: quickFilterTimes,
            activeQuickTimeFilter: selectedQuickTimeFilterIndex,
            pendingTimeFilter: quickFilterTimes,
            pendingQuickTimeFilter: selectedQuickTimeFilterIndex,
            dateTimePickerOpened: false,
            // Quick filter times are already UTC
            startTime: quickFilterTimes.startDate,
            endTime: quickFilterTimes.endDate,
          });
        } else {
          // Custom date - DON'T overwrite startTime/endTime (they should already be UTC)
          // If store already has valid times (set by initializeFromUrlParams), 
          // use those for display instead of empty args
          const hasExistingTimes = currentState.startTime && currentState.endTime;
          const hasExistingDisplay = currentState.selectedTimeFilter?.startDate && 
                                     currentState.selectedTimeFilter?.endDate;
          
          // If store already has valid data from URL params, skip re-initialization
          if (hasExistingTimes && hasExistingDisplay) {
            return;
          }
          
          // Only update if we have valid display times from args
          if (startTimeArg && endTimeArg) {
            const displayTimeFilter = {
              startDate: startTimeArg,
              endDate: endTimeArg,
              };

        set({
              // Display times (local) - for the date picker UI
              selectedTimeFilter: displayTimeFilter,
          activeQuickTimeFilter: selectedQuickTimeFilterIndex,
              pendingTimeFilter: displayTimeFilter,
              pendingQuickTimeFilter: selectedQuickTimeFilterIndex,
          dateTimePickerOpened: false,
              // DO NOT overwrite startTime/endTime here - they should remain UTC
              // They are set by initializeFromUrlParams or handleDateTimeApply
        });
          }
        }
      },

      handleDateTimeApply: (value, searchParams, handleTimefilterChange) => {
        const { pendingQuickTimeFilter } = get();
        let newSearchParams;
        if (pendingQuickTimeFilter !== -1) {
          newSearchParams = filtersToQueryString({
            ...Object.fromEntries(searchParams.entries()),
            quickDateFilter: `${pendingQuickTimeFilter}`,
            startDate: "",
            endDate: "",
          });
        } else {
          // Store UTC times in URL (universal format for sharing)
          newSearchParams = filtersToQueryString({
            ...Object.fromEntries(searchParams.entries()),
            quickDateFilter: "-1",
            startDate: value?.startDate || "",
            endDate: value?.endDate || "",
          });
        }

        // Apply pending state to main state
        // value contains UTC times from the dropdown
        set({
          // Store UTC times for query usage
          startTime: value?.startDate || "",
          endTime: value?.endDate || "",
          // Store local times for display
          selectedTimeFilter: {
            startDate:
              getLocalStringFromUTCDateTimeValue(value?.startDate) || "",
            endDate: getLocalStringFromUTCDateTimeValue(value?.endDate) || "",
          },
          pendingTimeFilter: {
            startDate:
              getLocalStringFromUTCDateTimeValue(value?.startDate) || "",
            endDate: getLocalStringFromUTCDateTimeValue(value?.endDate) || "",
          },
          activeQuickTimeFilter: pendingQuickTimeFilter,
          pendingQuickTimeFilter: pendingQuickTimeFilter,
          dateTimePickerOpened: false,
          quickTimeRangeFilterIndex: pendingQuickTimeFilter,
        });
        handleTimefilterChange(value);
        return newSearchParams;
      },

      handleDateTimeReset: (
        defaultQuickTimeOptions,
        defaultQuickTimeFilterIndex,
        subractMinutes,
        searchParams,
        handleTimefilterChange,
      ) => {
        const defaultStartEndDateTimeType: StartEndDateTimeType =
          getStartAndEndDateTimeString(
            defaultQuickTimeOptions[defaultQuickTimeFilterIndex].value,
            subractMinutes,
          );

        const newSearchParams = filtersToQueryString({
          ...Object.fromEntries(searchParams.entries()),
          quickDateFilter: `${defaultQuickTimeFilterIndex}`,
          startDate: "",
          endDate: "",
        });

        // Reset both applied and pending state
        // defaultStartEndDateTimeType contains UTC times from getStartAndEndDateTimeString
        set({
          startTime: defaultStartEndDateTimeType.startDate,
          endTime: defaultStartEndDateTimeType.endDate,
          selectedTimeFilter: defaultStartEndDateTimeType,
          activeQuickTimeFilter: defaultQuickTimeFilterIndex,
          pendingTimeFilter: defaultStartEndDateTimeType,
          pendingQuickTimeFilter: defaultQuickTimeFilterIndex,
          dateTimePickerOpened: false,
        });

        handleTimefilterChange(defaultStartEndDateTimeType);
        return newSearchParams;
      },

      getDateTimeDisplayText: () => {
        const { activeQuickTimeFilter, selectedTimeFilter } = get();

        if (activeQuickTimeFilter === -1) {
          const startDate = selectedTimeFilter?.startDate;
          const endDate = selectedTimeFilter?.endDate;
          
          // Validate dates before formatting
          const startParsed = startDate ? dayjs(startDate) : null;
          const endParsed = endDate ? dayjs(endDate) : null;
          
          const startFormatted = startParsed?.isValid() 
            ? startParsed.format(DATE_FORMAT) 
            : "Select start";
          const endFormatted = endParsed?.isValid() 
            ? endParsed.format(DATE_FORMAT) 
            : "Select end";
          
          return `${startFormatted} - ${endFormatted}`;
        }

        return CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS[
          activeQuickTimeFilter
        ].label;
      },

      handleDateTimeRefreshClick: (subractMinutes, handleTimefilterChange) => {
        const { activeQuickTimeFilter, selectedTimeFilter } = get();
        
        // If custom date is selected (-1), just keep the same time range
        // (no refresh makes sense for custom dates)
        if (activeQuickTimeFilter === -1) {
          // For custom dates, we don't change the time range on refresh
          // Just trigger the callback with existing times
          handleTimefilterChange(selectedTimeFilter);
          return;
        }
        
        // Validate that the index is within bounds
        if (activeQuickTimeFilter < 0 || activeQuickTimeFilter >= CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS.length) {
          // Fallback to default if invalid
          const refreshedTimeFilter = getStartAndEndDateTimeString(
            CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS[DEFAULT_QUICK_TIME_FILTER_INDEX].value,
            subractMinutes,
          );
          set({ 
            selectedTimeFilter: refreshedTimeFilter,
            startTime: refreshedTimeFilter.startDate,
            endTime: refreshedTimeFilter.endDate,
            timeFilterOptions: refreshedTimeFilter,
            pendingTimeFilter: refreshedTimeFilter,
            pendingQuickTimeFilter: DEFAULT_QUICK_TIME_FILTER_INDEX,
            activeQuickTimeFilter: DEFAULT_QUICK_TIME_FILTER_INDEX,
          });
          handleTimefilterChange(refreshedTimeFilter);
          return;
        }
        
        const filterOption = CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS[activeQuickTimeFilter];
        
        const refreshedTimeFilter = getStartAndEndDateTimeString(
          filterOption.value,
          subractMinutes,
        );

        // Update all time-related state directly, including quickTimeRangeString
        set({ 
          selectedTimeFilter: refreshedTimeFilter,
          startTime: refreshedTimeFilter.startDate,
          endTime: refreshedTimeFilter.endDate,
          timeFilterOptions: refreshedTimeFilter,
          // Update quickTimeRangeString to prevent re-initialization with empty string
          quickTimeRangeString: filterOption.value,
          quickTimeRangeFilterIndex: activeQuickTimeFilter,
          // Also sync pending state
          pendingTimeFilter: refreshedTimeFilter,
          pendingQuickTimeFilter: activeQuickTimeFilter,
        });
        
        // Call the callback to notify the component
        handleTimefilterChange(refreshedTimeFilter);
      },
      
      syncPendingWithApplied: () => {
        const { selectedTimeFilter, activeQuickTimeFilter } = get();
        set({
          pendingTimeFilter: selectedTimeFilter,
          pendingQuickTimeFilter: activeQuickTimeFilter,
        });
      },
    }),
    { name: "FilterStore" },
  ),
);
