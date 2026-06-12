import { useListState } from "@mantine/hooks";
import { GlobalBlacklistEventSequenceProps } from "./GlobalBlacklistEventSequence.interface";
import { useEffect } from "react";
import { Box, Button, Text, Tooltip, useMantineTheme } from "@mantine/core";
import classes from "./GlobalBlacklistEventSequence.module.css";
import { CRITICAL_INTERACTION_FORM_CONSTANTS } from "../../../../constants";
import { GlobalBlacklistEventSequenceItem } from "../GlobalBlacklistEventSequenceItem";
import { FilterInputFeild } from "../EventsSequenceSection";
import {
  EventFilters,
  FilterOperator,
} from "../../CriticalInteractionForm.interface";
import { IconInfoCircle } from "@tabler/icons-react";

export function GlobalBlackListEventSequence({
  defaultState,
  onEventsGlobalBlackListingDataChange,
  onNextClick,
  onBackClick,
  onChangeInActiveState,
}: GlobalBlacklistEventSequenceProps) {
  const theme = useMantineTheme();
  const [state, handlers] = useListState<EventFilters>(defaultState);
  const disableButton = state.length > 0 && !state[state.length - 1]?.name;

  useEffect(() => {
    onChangeInActiveState(!disableButton);
    onEventsGlobalBlackListingDataChange(state);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state, disableButton]);

  const onAddEventButtonClick = (event: any) => {
    event.preventDefault();
    handlers.append({
      name: "",
      props: [],
      isBlacklisted: true,
    });
  };

  const onEventNameChange = (
    index: number,
    item: EventFilters,
    eventName: string,
  ) => {
    handlers.setItem(index, {
      ...item,
      name: eventName,
    });
  };

  const onDeleteFilterButtonClick = (
    index: number,
    filterIndex: number,
    item: EventFilters,
  ) => {
    const newPropsFilter = item?.props?.filter(
      (_, index) => index !== filterIndex,
    );

    handlers.setItem(index, {
      ...item,
      name: item?.name ?? "",
      props: newPropsFilter || [],
    });
  };

  const onDeleteButtonClick = (index: number) => {
    handlers.remove(index);
  };

  const onFilterValueChange = (
    index: number,
    filterIndex: number,
    filterValue: string,
    inputFeildName: FilterInputFeild,
    item: EventFilters,
  ) => {
    const filters = item?.props ?? [];

    if (!filters[filterIndex]) {
      return;
    }

    if (inputFeildName === "operator") {
      filters[filterIndex][inputFeildName] = filterValue as FilterOperator;
    } else {
      filters[filterIndex][inputFeildName] = filterValue;
    }
    handlers.setItem(index, {
      ...item,
      name: item?.name ?? "",
      props: filters,
    });
  };

  const onAddEventFilterButtonClick = (index: number, item: EventFilters) => {
    handlers.setItem(index, {
      ...item,
      name: item?.name ?? "",
      props: item?.props
        ? [
            ...item.props,
            {
              name: "",
              value: "",
              operator: "EQUALS" as FilterOperator,
            },
          ]
        : [
            {
              name: "",
              value: "",
              operator: "EQUALS" as FilterOperator,
            },
          ],
    });
  };

  return (
    <Box className={classes.globalBlacklistContainer}>
      <Box className={classes.sectionLabel}>
        <Text>
          {CRITICAL_INTERACTION_FORM_CONSTANTS.BLACKLIST_EVENTS_SEQUENCE_LABEL}
        </Text>
        <Tooltip
          withArrow
          multiline
          // w={200}
          label={
            CRITICAL_INTERACTION_FORM_CONSTANTS.GLOBAL_EVENTS_SEQUENCE_SUB_LABEL
          }
        >
          <IconInfoCircle
            color={theme.colors.primary[6]}
            size={25}
            className={classes.infoIcon}
          />
        </Tooltip>
      </Box>
      {!state.length && (
        <Box className={classes.infoBox}>
          <Text c={theme.colors.gray[6]}>
            {
              CRITICAL_INTERACTION_FORM_CONSTANTS.BLACKLIST_EVENTS_SEQUENCE_SUB_LABEL
            }
          </Text>
        </Box>
      )}
      <div className={classes.globalblacklistEventContainer}>
        {state.map((item, index) => (
          <GlobalBlacklistEventSequenceItem
            key={index}
            eventName={item.name}
            filters={item.props}
            showAddEventFilterButton={!!item.name}
            onEventNameChange={(eventName: string) =>
              onEventNameChange(index, item, eventName)
            }
            onDeleteFilterButtonClick={(filerIndex: number) =>
              onDeleteFilterButtonClick(index, filerIndex, item)
            }
            onDeleteButtonClick={() => onDeleteButtonClick(index)}
            onFilterValueChange={(
              filterIndex: number,
              filterValue: string,
              inputFeildName: FilterInputFeild,
            ) =>
              onFilterValueChange(
                index,
                filterIndex,
                filterValue,
                inputFeildName,
                item,
              )
            }
            onAddEventFilterButtonClick={() =>
              onAddEventFilterButtonClick(index, item)
            }
          />
        ))}
        <Button
          className={classes.addEvent}
          variant="transparent"
          size="md"
          disabled={disableButton}
          onClick={onAddEventButtonClick}
        >
          {"+ Add Event"}
        </Button>
      </div>
      <Box className={classes.sectionButtons}>
        <Button
          className={classes.sectionButton}
          variant="outline"
          size="md"
          onClick={onBackClick}
        >
          {"Back"}
        </Button>
        <Button
          className={classes.sectionButton}
          variant="filled"
          size="md"
          onClick={onNextClick}
          disabled={disableButton}
        >
          {CRITICAL_INTERACTION_FORM_CONSTANTS.NEXT_BUTTON_TEXT}
        </Button>
      </Box>
    </Box>
  );
}
