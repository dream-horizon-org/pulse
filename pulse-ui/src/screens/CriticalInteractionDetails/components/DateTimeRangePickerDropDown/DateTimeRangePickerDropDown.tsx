import classes from "./DateTimeRangePickerDropDown.module.css";
import { Button, Text, Grid, Divider } from "@mantine/core";
import { DateTimePicker, DateValue } from "@mantine/dates";
import { QuickDateTimeFilter } from "../DateTimeRangePicker/QuickDateTimeFilter";
import { StartEndDateTimeType } from "./DateTimeRangePicker.interface";
import { TimeFilter } from "../../CriticalInteractionDetails.interface";
import { useEffect, useState } from "react";
import {
  getDateTimeStringFromDateValue,
  getUTCDateTimeFromLocalStringDateValue,
  getStartAndEndDateTimeString,
} from "../../../../utils/DateUtil";
import dayjs from "dayjs";
import { DATE_TIME_RANGE_PICKER_DEFAULT_TIME_DIFFERENCE } from "./DateTimeRangePickerDropDown.constants";
import { DATE_FORMAT } from "../../../../constants";

export const DateTimeRangePickerDropDown = ({
  quickTimeOptions,
  activeQuickTimeFilter: initialActiveQuickTimeFilter,
  defaultQuickTimeFilterString,
  maxDateTime = null,
  minDateTime = null,
  defaultStartTime,
  defaultEndTime,
  subtractMinutes = 0,
  applyButtonText = "Apply",
  resetButtonText = "Reset",
  handleStartEndDateTimeChange,
  handleQuickTimeFilterChange,
  handleApplyButton,
  handleResetButton,
}: {
  quickTimeOptions: TimeFilter[];
  activeQuickTimeFilter: number;
  defaultQuickTimeFilterString: string;
  maxDateTime?: string | null;
  minDateTime?: string | null;
  defaultStartTime?: string;
  defaultEndTime?: string;
  subtractMinutes?: number;
  applyButtonText?: string;
  resetButtonText?: string;
  handleStartEndDateTimeChange: (value: StartEndDateTimeType) => void;
  handleQuickTimeFilterChange: (value: number) => void;
  handleApplyButton: (value: StartEndDateTimeType) => void;
  handleResetButton: () => void;
}) => {
  // Local state to track the currently selected quick filter within this dropdown
  const [localQuickTimeFilter, setLocalQuickTimeFilter] = useState<number>(initialActiveQuickTimeFilter);
  
  const [customStartEndDateTime, setCustomStartEndDateTime] = useState<
    StartEndDateTimeType | undefined
  >(getInitialCustomDateTimePicker());

  const [disableApplyButton, setDisableApplyButton] = useState<boolean>(false);

  function getInitialCustomDateTimePicker() {
    if (initialActiveQuickTimeFilter !== -1) {
      return undefined;
    }

    return {
      startDate: defaultStartTime,
      endDate: defaultEndTime,
    };
  }

  // Sync local state when prop changes (e.g., when dropdown reopens)
  useEffect(() => {
    setLocalQuickTimeFilter(initialActiveQuickTimeFilter);
  }, [initialActiveQuickTimeFilter]);

  const handleQuickTimeFilterChangeSelf = (value: number) => {
    setLocalQuickTimeFilter(value);
    setCustomStartEndDateTime(undefined);
    setDisableApplyButton(false);
    handleQuickTimeFilterChange(value);
  };

  const handlCustomStartDate = (value: DateValue) => {
    setCustomStartEndDateTime((prev) => {
      const newStartDate = getDateTimeStringFromDateValue(value);
      let updatedEndDate = prev?.endDate;

      if (value && prev?.endDate) {
        const startTime = dayjs(newStartDate);
        const endTime = dayjs(prev.endDate);

        if (startTime.isAfter(endTime) || startTime.isSame(endTime)) {
          updatedEndDate = startTime
            .add(DATE_TIME_RANGE_PICKER_DEFAULT_TIME_DIFFERENCE, "minute")
            .format("YYYY-MM-DD HH:mm:ss");
        }
      }

      return {
        ...prev,
        startDate: newStartDate,
        endDate: updatedEndDate,
      };
    });
  };

  const handlCustomEndDate = (value: DateValue) => {
    setCustomStartEndDateTime((prev) => {
      const newEndDate = getDateTimeStringFromDateValue(value);

      if (value && prev?.startDate) {
        const startTime = dayjs(prev.startDate);
        const endTime = dayjs(newEndDate);

        if (endTime.isBefore(startTime) || endTime.isSame(startTime)) {
          const updatedEndDate = startTime
            .add(DATE_TIME_RANGE_PICKER_DEFAULT_TIME_DIFFERENCE, "minute")
            .format("YYYY-MM-DD HH:mm:ss");
          return {
            ...prev,
            endDate: updatedEndDate,
          };
        }
      }

      return {
        ...prev,
        endDate: newEndDate,
      };
    });
  };

  const handleApplySelf = () => {
    if (localQuickTimeFilter !== -1) {
      const item = quickTimeOptions[localQuickTimeFilter];
      if (item) {
        handleApplyButton(
          getStartAndEndDateTimeString(item.value, subtractMinutes),
        );
      }

      return;
    }

    if (customStartEndDateTime) {
      return handleApplyButton({
        startDate: getUTCDateTimeFromLocalStringDateValue(
          customStartEndDateTime.startDate,
        ),
        endDate: getUTCDateTimeFromLocalStringDateValue(
          customStartEndDateTime.endDate,
        ),
      });
    }

    return handleApplyButton({
      startDate: getUTCDateTimeFromLocalStringDateValue(defaultStartTime),
      endDate: getUTCDateTimeFromLocalStringDateValue(defaultEndTime),
    });
  };

  useEffect(() => {
    if (localQuickTimeFilter !== -1) {
      // Initialize custom date/time picker state based on initial filter
      setCustomStartEndDateTime(undefined);
      setDisableApplyButton(false);
    }
    // eslint-disable-next-line
  }, []);

  const getMinEndDate = () => {
    if (customStartEndDateTime?.startDate) {
      return dayjs(customStartEndDateTime.startDate)
        .add(DATE_TIME_RANGE_PICKER_DEFAULT_TIME_DIFFERENCE, "minute")
        .toDate();
    }
    return minDateTime ? new Date(minDateTime) : undefined;
  };

  useEffect(() => {
    if (customStartEndDateTime?.startDate && customStartEndDateTime?.endDate) {
      handleStartEndDateTimeChange({
        startDate: customStartEndDateTime.startDate,
        endDate: customStartEndDateTime.endDate,
      });

      const start = dayjs(customStartEndDateTime.startDate);
      const end = dayjs(customStartEndDateTime.endDate);
      setDisableApplyButton(end.isBefore(start) || end.isSame(start));
      handleQuickTimeFilterChange(-1);
    }

    // eslint-disable-next-line
  }, [
    customStartEndDateTime,
    customStartEndDateTime?.endDate,
    customStartEndDateTime?.startDate,
  ]);

  return (
    <>
      <Grid>
        <Grid.Col span={6}>
          <Text className={classes.timeFilterPopupTitle} size="sm">
            Custom range
          </Text>
          <DateTimePicker
            value={
              customStartEndDateTime?.startDate
                ? new Date(customStartEndDateTime?.startDate)
                : undefined
            }
            valueFormat={DATE_FORMAT}
            size="xs"
            withSeconds
            label="Start time"
            className={classes.dateTimeRangePicker}
            placeholder="Pick date and time"
            minDate={minDateTime ? new Date(minDateTime) : undefined}
            maxDate={maxDateTime ? new Date(maxDateTime) : new Date(Date.now())}
            onChange={handlCustomStartDate}
            popoverProps={{ withinPortal: false }}
          />
          <DateTimePicker
            value={
              customStartEndDateTime?.endDate
                ? new Date(customStartEndDateTime?.endDate)
                : undefined
            }
            valueFormat={DATE_FORMAT}
            size="xs"
            withSeconds
            label="End time"
            className={classes.dateTimeRangePicker}
            placeholder="Pick date and time"
            minDate={getMinEndDate()}
            maxDate={maxDateTime ? new Date(maxDateTime) : new Date(Date.now())}
            onChange={handlCustomEndDate}
            popoverProps={{ withinPortal: false }}
          />
        </Grid.Col>
        <Grid.Col span={1}>
          <Divider orientation="vertical" h={"200px"} />
        </Grid.Col>
        <Grid.Col span={5}>
          <Text className={classes.timeFilterPopupTitle} size="sm">
            Quick time filter
          </Text>
          <QuickDateTimeFilter
            handleChangeFilter={handleQuickTimeFilterChangeSelf}
            active={localQuickTimeFilter}
            defaultQuickTimeOptions={quickTimeOptions}
          />
        </Grid.Col>
      </Grid>
      <Divider />
      <div className={classes.filterApplyButtonContainer}>
        <Button variant="outline" onClick={handleResetButton}>
          {resetButtonText}
        </Button>
        <Button
          disabled={disableApplyButton}
          variant="filled"
          onClick={handleApplySelf}
        >
          {applyButtonText}
        </Button>
      </div>
    </>
  );
};
