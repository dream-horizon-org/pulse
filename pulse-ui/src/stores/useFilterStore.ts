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
}

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
  activeQuickTimeFilter: 1,
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
        if (Number(params.quickDateFilter || 1) !== -1) {
          const dateTimeFilter = getStartAndEndDateTimeString(
            CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS[
              Number(params.quickDateFilter || 1)
            ].value,
            2,
          );

          set({
            quickTimeRangeString:
              CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS[
                Number(params.quickDateFilter || 1)
              ].value,
            startTime: dateTimeFilter.startDate,
            endTime: dateTimeFilter.endDate,
            quickTimeRangeFilterIndex: Number(params.quickDateFilter || 1),
            filterValues,
          });
        } else if (
          params.quickDateFilter &&
          Number(params.quickDateFilter) === -1
        ) {
          set({
            startTime: params.startDate || "",
            endTime: params.endDate || "",
            quickTimeRangeFilterIndex: -1,
            quickTimeRangeString: "",
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
        set({ activeQuickTimeFilter: value });
      },

      handleStartEndDateTimeChange: (value) => {
        const { selectedTimeFilter } = get();
        set({
          selectedTimeFilter: {
            ...selectedTimeFilter,
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
        startTime,
        endTime,
        subtractMinutes = 2,
      ) => {
        const currentState = get();
        
        // If the incoming times match what's already in the store, skip re-initialization
        // This prevents overwriting times that were just set by refresh
        if (currentState.startTime === startTime && currentState.endTime === endTime) {
          return;
        }
        
        const defaultSelectedTimeFilter =
          selectedQuickTimeFilterIndex !== -1
            ? getStartAndEndDateTimeString(
                quickTimeRangeString,
                subtractMinutes,
              )
            : {
                startDate: startTime,
                endDate: endTime,
              };

        set({
          selectedTimeFilter: defaultSelectedTimeFilter,
          activeQuickTimeFilter: selectedQuickTimeFilterIndex,
          dateTimePickerOpened: false,
          startTime: defaultSelectedTimeFilter.startDate,
          endTime: defaultSelectedTimeFilter.endDate,
        });
      },

      handleDateTimeApply: (value, searchParams, handleTimefilterChange) => {
        const { activeQuickTimeFilter } = get();
        let newSearchParams;
        if (activeQuickTimeFilter !== -1) {
          newSearchParams = filtersToQueryString({
            ...Object.fromEntries(searchParams.entries()),
            quickDateFilter: `${activeQuickTimeFilter}`,
            startDate: "",
            endDate: "",
          });
        } else {
          newSearchParams = filtersToQueryString({
            ...Object.fromEntries(searchParams.entries()),
            quickDateFilter: "-1",
            startDate:
              getLocalStringFromUTCDateTimeValue(value?.startDate) || "",
            endDate: getLocalStringFromUTCDateTimeValue(value?.endDate) || "",
          });
        }

        set({
          selectedTimeFilter: {
            startDate:
              getLocalStringFromUTCDateTimeValue(value?.startDate) || "",
            endDate: getLocalStringFromUTCDateTimeValue(value?.endDate) || "",
          },
          dateTimePickerOpened: false,
          quickTimeRangeFilterIndex: activeQuickTimeFilter,
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

        set({
          selectedTimeFilter: defaultStartEndDateTimeType,
          activeQuickTimeFilter: defaultQuickTimeFilterIndex,
          dateTimePickerOpened: false,
        });

        handleTimefilterChange(defaultStartEndDateTimeType);
        return newSearchParams;
      },

      getDateTimeDisplayText: () => {
        const { activeQuickTimeFilter, selectedTimeFilter } = get();

        if (activeQuickTimeFilter === -1) {
          return `${dayjs(selectedTimeFilter?.startDate).format(DATE_FORMAT)} - ${dayjs(selectedTimeFilter?.endDate).format(DATE_FORMAT)}`;
        }

        return CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS[
          activeQuickTimeFilter
        ].label;
      },

      handleDateTimeRefreshClick: (subractMinutes, handleTimefilterChange) => {
        const { activeQuickTimeFilter } = get();
        const refreshedTimeFilter = getStartAndEndDateTimeString(
          CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS[
            activeQuickTimeFilter
          ].value,
          subractMinutes,
        );

        // Update all time-related state directly
        set({ 
          selectedTimeFilter: refreshedTimeFilter,
          startTime: refreshedTimeFilter.startDate,
          endTime: refreshedTimeFilter.endDate,
          timeFilterOptions: refreshedTimeFilter,
        });
        
        // Call the callback to notify the component
        handleTimefilterChange(refreshedTimeFilter);
      },
    }),
    { name: "FilterStore" },
  ),
);
