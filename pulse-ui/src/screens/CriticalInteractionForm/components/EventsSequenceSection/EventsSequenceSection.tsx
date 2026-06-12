import { useListState } from "@mantine/hooks";
import { useEffect, useRef } from "react";
import {
  DragDropContext,
  DropResult,
  Droppable,
  ResponderProvided,
} from "@hello-pangea/dnd";
import {
  Box,
  Button,
  Divider,
  Text,
  Tooltip,
  useMantineTheme,
} from "@mantine/core";
import { CRITICAL_INTERACTION_FORM_CONSTANTS } from "../../../../constants";
import classes from "./EventsSequenceSection.module.css";
import { IconInfoCircle } from "@tabler/icons-react";
import { v4 as uuidv4 } from "uuid";
import {
  EventsSequenceSectionProps,
  FilterInputFeild,
} from "./EventsSequenceSection.interface";
import {
  EventSequenceData,
  FilterOperator,
} from "../../CriticalInteractionForm.interface";
import { EventSequenceItem } from "../EventSequenceItem";

export function EventsSequenceSection({
  defaultState,
  onEventsSequenceWhitelistingDataChange,
  onNextClick,
  onBackClick,
  onChangeInActiveState,
}: EventsSequenceSectionProps) {
  const theme = useMantineTheme();

  const [state, handlers] = useListState<EventSequenceData>(defaultState);

  const areAllEventNamesPresent = useRef<boolean>(false);

  areAllEventNamesPresent.current = state.every(
    (eventData) => eventData.name,
  );

  useEffect(() => {
    onChangeInActiveState(areAllEventNamesPresent.current);
    onEventsSequenceWhitelistingDataChange(state);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  const onEventNameChange = (
    index: number,
    item: EventSequenceData,
    eventName: string,
  ) => {
    handlers.setItem(index, {
      ...item,
      name: eventName || "",
    });
  };

  const onAddEventFilterButtonClick = (
    index: number,
    item: EventSequenceData,
  ) => {
    handlers.setItem(index, {
      ...item,
      props: item.props
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

  const onBlacklistEventButtonClick = (
    index: number,
    item: EventSequenceData,
  ) => {
    handlers.setItem(index, {
      ...item,
      isBlacklisted: !item.isBlacklisted,
    });
  };

  const onFilterValueChange = (
    index: number,
    filterIndex: number,
    filterValue: string,
    inputFeildName: FilterInputFeild,
    item: EventSequenceData,
  ) => {
    const filters = item.props ?? [];

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
      props: filters,
    });
  };

  const onDeleteButtonClick = (index: number) => {
    handlers.remove(index);
  };

  const onDeleteFilterButtonClick = (
    index: number,
    filterIndex: number,
    item: EventSequenceData,
  ) => {
    const newPropsFilter = item.props?.filter(
      (_, index) => index !== filterIndex,
    );

    handlers.setItem(index, {
      ...item,
      props: newPropsFilter || [],
    });
  };

  const onAddEventButtonClick = (event: any) => {
    event.preventDefault();
    handlers.insert(state.length - 1, {
      draggableId: uuidv4(),
      name: "",
      props: [],
      isBlacklisted: false,
    });
  };

  const onDragEnd = (result: DropResult, _provided: ResponderProvided) => {
    handlers.reorder({
      from: result.source.index,
      to: result.destination?.index ?? 1,
    });

    handlers.setItemProp(0, "isBlacklisted", false);
    handlers.setItemProp(state.length - 1, "isBlacklisted", false);
  };

  return (
    <Box className={classes.eventsSequenceContainer}>
      <DragDropContext onDragEnd={onDragEnd}>
        <Box className={classes.sectionLabel}>
          <Text>
            {CRITICAL_INTERACTION_FORM_CONSTANTS.EVENTS_SEQUENCE_LABEL}
          </Text>
          <Tooltip
            withArrow
            multiline
            // w={200}
            label={
              CRITICAL_INTERACTION_FORM_CONSTANTS.EVENTS_SEQUENCE_SUB_LABEL
            }
          >
            <IconInfoCircle
              color={theme.colors.primary[6]}
              size={25}
              className={classes.infoIcon}
            />
          </Tooltip>
        </Box>

        <div className={classes.eventsSequenceSectionContainer}>
          <Divider
            orientation="vertical"
            className={classes.eventSequenceContainerDivider}
          />
          <Droppable droppableId="dnd-list" direction="vertical">
            {(provided) => (
              <div
                {...provided.droppableProps}
                ref={provided.innerRef}
                className={classes.droppableContainer}
              >
                <>
                  {state.map((item, index) => (
                    <div key={item.draggableId}>
                      {index === state.length - 1 && (
                        <Button
                          className={classes.addEvent}
                          variant="transparent"
                          size="md"
                          onClick={onAddEventButtonClick}
                        >
                          + Add event
                        </Button>
                      )}
                      <EventSequenceItem
                        key={item.draggableId}
                        totalItemsLength={state.length}
                        index={index}
                        showAddEventFilterButton={!!item.name}
                        draggableId={item.draggableId}
                        isEventBlackListed={item.isBlacklisted}
                        onDeleteButtonClick={() => onDeleteButtonClick(index)}
                        onBlacklistEventButtonClick={(event: any) => {
                          event.preventDefault();
                          onBlacklistEventButtonClick(index, item);
                        }}
                        onDeleteFilterButtonClick={(filterIndex: number) =>
                          onDeleteFilterButtonClick(index, filterIndex, item)
                        }
                        onFilterValueChange={(
                          filterIndex: number,
                          filterValue: string,
                          inputFeildName: "name" | "value" | "operator",
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
                        onEventNameChange={(eventName: string) =>
                          onEventNameChange(index, item, eventName)
                        }
                        eventData={item}
                      />
                    </div>
                  ))}
                  {provided.placeholder}
                </>
              </div>
            )}
          </Droppable>
        </div>
      </DragDropContext>

      <Box className={classes.sectionButtons}>
        {/* <Button
          className={classes.sectionButton}
          variant="filled"
          size="md"
          disabled={!areAllEventNamesPresent.current}
          onClick={onAddEventButtonClick}
        >
          {"Add Event"}
        </Button> */}
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
          disabled={!areAllEventNamesPresent.current}
          onClick={onNextClick}
        >
          {CRITICAL_INTERACTION_FORM_CONSTANTS.NEXT_BUTTON_TEXT}
        </Button>
      </Box>
    </Box>
  );
}
