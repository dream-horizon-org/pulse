import { Box, Chip, Divider, Text, useMantineTheme } from "@mantine/core";
import classes from "./UserActionCategories.module.css";
import {
  COMMON_CONSTANTS,
  CRITICAL_INTERACTION_FORM_CONSTANTS,
  EVENTS_THRESHOLDS_PAGE_CONSTANTS,
  USER_ACTION_CATEGORIES,
  USER_ACTION_CATEGORIES_KEY_VALUE,
} from "../../../../constants";
import { showNotification } from "../../../../helpers/showNotification";
import { IconSquareRoundedX } from "@tabler/icons-react";
import { useEffect, useState } from "react";
export type UserActionCategoriesProps = {
  onInteractionCategoryChange: (low: number, mid: number, high: number) => void;
};

type UserInteractionCategoryThresholds = {
  upTimeLowerLimit: number;
  upTimeMidLimit: number;
  upTimeUpperLimit: number;
};

const userInteractionCategoryThresholds: Record<
  string,
  UserInteractionCategoryThresholds
> = {
  isInPlaceUpdateAction: {
    upTimeLowerLimit: 16,
    upTimeMidLimit: 50,
    upTimeUpperLimit: 100,
  },
  isTriggerNetworkRequestAction: {
    upTimeLowerLimit: 100,
    upTimeMidLimit: 500,
    upTimeUpperLimit: 1000,
  },
  isTriggerAnimationAction: {
    upTimeLowerLimit: 200,
    upTimeMidLimit: 500,
    upTimeUpperLimit: 1000,
  },
  isAppLaunchInteractionAction: {
    upTimeLowerLimit: 1500,
    upTimeMidLimit: 3000,
    upTimeUpperLimit: 5000,
  },
};

export function UserActionCategories({
  onInteractionCategoryChange,
}: UserActionCategoriesProps) {
  const theme = useMantineTheme();
  const [
    selectedUserInteractionCategories,
    setSelectedUserInteractionCategories,
  ] = useState<string[]>([USER_ACTION_CATEGORIES.IN_PLACE_UPDATE]);

  useEffect(() => {
    onInteractionCategoryChange(
      userInteractionCategoryThresholds["isInPlaceUpdateAction"]
        .upTimeLowerLimit,
      userInteractionCategoryThresholds["isInPlaceUpdateAction"].upTimeMidLimit,
      userInteractionCategoryThresholds["isInPlaceUpdateAction"]
        .upTimeUpperLimit,
    );
  }, [onInteractionCategoryChange]);

  const [thresholds, setThresholds] = useState({
    lower:
      userInteractionCategoryThresholds["isInPlaceUpdateAction"]
        .upTimeLowerLimit,
    mid: userInteractionCategoryThresholds["isInPlaceUpdateAction"]
      .upTimeMidLimit,
    upper:
      userInteractionCategoryThresholds["isInPlaceUpdateAction"]
        .upTimeUpperLimit,
  });

  const onChange = (values: Array<string>) => {
    if (!values.includes(USER_ACTION_CATEGORIES.IN_PLACE_UPDATE)) {
      showNotification(
        COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
        EVENTS_THRESHOLDS_PAGE_CONSTANTS.IN_PLACE_DESELECTION_ERROR_MESSAGE,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
      return;
    }

    let upTimeLowerLimit = 0;
    let upTimeMidLimit = 0;
    let upTimeUpperLimit = 0;

    for (const category in userInteractionCategoryThresholds) {
      if (values.includes(category)) {
        upTimeLowerLimit +=
          userInteractionCategoryThresholds[category].upTimeLowerLimit;
        upTimeMidLimit +=
          userInteractionCategoryThresholds[category].upTimeMidLimit;
        upTimeUpperLimit +=
          userInteractionCategoryThresholds[category].upTimeUpperLimit;
      }
    }

    setSelectedUserInteractionCategories(values);
    setThresholds({
      lower: upTimeLowerLimit,
      mid: upTimeMidLimit,
      upper: upTimeUpperLimit,
    });
  };

  useEffect(() => {
    onInteractionCategoryChange(
      thresholds.lower,
      thresholds.mid,
      thresholds.upper,
    );
  }, [
    onInteractionCategoryChange,
    thresholds.lower,
    thresholds.mid,
    thresholds.upper,
  ]);

  return (
    <>
      <Text
        c={theme.colors.gray[9]}
        className={classes.userActionCategoriesLabel}
      >
        {CRITICAL_INTERACTION_FORM_CONSTANTS.SELECT_CATEGORIES_MESSAGE}
      </Text>
      <Box className={classes.userActionCategoriesContainer}>
        <Box className={classes.chipGroup}>
          <Chip.Group
            multiple={true}
            value={selectedUserInteractionCategories}
            onChange={onChange}
          >
            {Object.keys(USER_ACTION_CATEGORIES_KEY_VALUE).map(
              (category, index) => (
                <Chip className={classes.chip} key={index} value={category}>
                  {USER_ACTION_CATEGORIES_KEY_VALUE[category]}
                </Chip>
              ),
            )}
          </Chip.Group>
        </Box>
      </Box>
      <Box className={classes.thresholds}>
        <span>Low: {thresholds.lower}</span>
        <span>Mid: {thresholds.mid}</span>
        <span>High: {thresholds.upper}</span>
      </Box>
      <Divider className={classes.dividerThresholds} />
    </>
  );
}
