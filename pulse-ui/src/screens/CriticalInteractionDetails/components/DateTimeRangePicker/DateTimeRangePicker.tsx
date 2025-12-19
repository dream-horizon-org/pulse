import classes from "./DateTimeRangePicker.module.css";
import {
  Popover,
  Button,
  Divider,
  Tooltip,
  UnstyledButton,
} from "@mantine/core";
import { IconClock, IconRefresh } from "@tabler/icons-react";
import { useEffect } from "react";
import { StartEndDateTimeType } from "../DateTimeRangePickerDropDown/DateTimeRangePicker.interface";
import { DateTimeRangePickerDropDown } from "../DateTimeRangePickerDropDown/DateTimeRangePickerDropDown";
import {
  CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS,
  TOOLTIP_LABLES,
  DEFAULT_QUICK_TIME_FILTER_INDEX,
} from "../../../../constants";
import React from "react";
import { useSearchParams } from "react-router-dom";
import { TimeFilter } from "../../CriticalInteractionDetails.interface";
import { useFilterStore } from "../../../../stores/useFilterStore";

const DateTimeRangePicker = ({
  handleTimefilterChange,
  defaultQuickTimeFilterString,
  selectedQuickTimeFilterIndex,
  defaultQuickTimeFilterIndex = DEFAULT_QUICK_TIME_FILTER_INDEX,
  defaultStartTime,
  defaultEndTime,
  showRefreshButton = true,
  maxDateTime = null,
  minDateTime = null,
  defaultQuickTimeOptions = CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS,
  resetKey = 0,
  subractMinutes = 2,
  className,
}: {
  handleTimefilterChange: (value: StartEndDateTimeType) => void;
  defaultQuickTimeFilterString: string;
  selectedQuickTimeFilterIndex: number;
  defaultQuickTimeFilterIndex?: number;
  defaultStartTime: string;
  defaultEndTime: string;
  showRefreshButton?: boolean;
  maxDateTime?: string | null;
  minDateTime?: string | null;
  defaultQuickTimeOptions?: Array<TimeFilter>;
  resetKey?: number;
  subractMinutes?: number;
  className?: string;
}) => {
  const [searchParams, setSearchParams] = useSearchParams();

  const {
    dateTimePickerOpened,
    selectedTimeFilter,
    activeQuickTimeFilter,
    toggleDateTimePickerOpened,
    handleQuickTimeFilterChange,
    handleStartEndDateTimeChange,
    initializeDateTimePickerState,
    handleDateTimeApply,
    handleDateTimeReset,
    getDateTimeDisplayText,
    handleDateTimeRefreshClick,
  } = useFilterStore();
  const handleApplyButton = (value: StartEndDateTimeType) => {
    const newSearchParams = handleDateTimeApply(
      value,
      searchParams,
      handleTimefilterChange,
    );
    setSearchParams(newSearchParams);
  };

  const handleResetButton = () => {
    const newSearchParams = handleDateTimeReset(
      defaultQuickTimeOptions,
      defaultQuickTimeFilterIndex,
      subractMinutes,
      searchParams,
      handleTimefilterChange,
    );
    setSearchParams(newSearchParams);
  };

  useEffect(() => {
    initializeDateTimePickerState(
      selectedQuickTimeFilterIndex,
      defaultQuickTimeFilterString,
      defaultStartTime,
      defaultEndTime,
      subractMinutes,
    );
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [defaultStartTime, defaultEndTime]);

  useEffect(() => {
    if (resetKey > 0) handleResetButton();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resetKey]);

  const handleRefreshButtonClick = () => {
    handleDateTimeRefreshClick(subractMinutes, handleTimefilterChange);
  };

  return (
    <div className={classes.dateTimeRangePickerContainer}>
      <Popover
        opened={dateTimePickerOpened}
        onChange={toggleDateTimePickerOpened}
        width={200}
        position="bottom"
        withArrow
        shadow="md"
        closeOnEscape
        closeOnClickOutside
      >
        <Popover.Target>
          <Button
            variant="transparent"
            size="compact-sm"
            onClick={toggleDateTimePickerOpened}
            className={classes.filterButton + " " + className}
          >
            <IconClock size={14} stroke={2.5} className={classes.filterIcon} />
            {getDateTimeDisplayText()}
          </Button>
        </Popover.Target>
        <Popover.Dropdown w={500} className={classes.timeFilterPopup}>
          <DateTimeRangePickerDropDown
            defaultQuickTimeFilterString={defaultQuickTimeFilterString}
            activeQuickTimeFilter={activeQuickTimeFilter}
            quickTimeOptions={defaultQuickTimeOptions}
            maxDateTime={maxDateTime}
            minDateTime={minDateTime}
            defaultStartTime={selectedTimeFilter?.startDate || defaultStartTime}
            defaultEndTime={selectedTimeFilter?.endDate || defaultEndTime}
            handleStartEndDateTimeChange={handleStartEndDateTimeChange}
            handleQuickTimeFilterChange={handleQuickTimeFilterChange}
            subtractMinutes={subractMinutes}
            handleApplyButton={handleApplyButton}
            handleResetButton={handleResetButton}
          ></DateTimeRangePickerDropDown>
        </Popover.Dropdown>
      </Popover>
      {activeQuickTimeFilter !== -1 && showRefreshButton && (
        <>
          <Divider orientation="vertical" />
          <UnstyledButton
            className={classes.refreshButton}
            onClick={handleRefreshButtonClick}
            variant="transparent"
            // size="compact-sm"
          >
            <Tooltip withArrow label={TOOLTIP_LABLES.REFRESH_BUTTON}>
              <IconRefresh size={16} strokeWidth={2.5} />
            </Tooltip>
          </UnstyledButton>
        </>
      )}
    </div>
  );
};

export default React.memo(DateTimeRangePicker);
