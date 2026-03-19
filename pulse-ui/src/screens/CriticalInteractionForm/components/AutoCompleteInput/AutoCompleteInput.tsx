import {
  GetScreeNameToEvenQueryMappingResponse,
  useGetScreenNameToEventQueryMapping,
} from "../../../../hooks/useGetScreenNameToEventQueryMapping";
import { AutoCompleteInputProps } from "./AutocompleteInput.interface";
import { Autocomplete, AutocompleteProps, Group, Text } from "@mantine/core";
import { CRITICAL_INTERACTION_FORM_CONSTANTS } from "../../../../constants";
import classes from "./AutoCompleteInput.module.css";
import { ApiResponse } from "../../../../helpers/makeRequest";
import { useCallback, useEffect, useMemo, useRef } from "react";
import { useDebouncedValue } from "@mantine/hooks";
import { EventsMetadata } from "../../CriticalInteractionForm.interface";

export function AutoCompleteInput(props: AutoCompleteInputProps) {
  const {
    eventName,
    onEventNameChange,
    onEventNameSelect,
    placeHolderText = CRITICAL_INTERACTION_FORM_CONSTANTS.AUTOCOMPLETE_PLACEHOLDER,
    badgeText = "",
  } = props;

  const eventMetadata = useRef<EventsMetadata>({});
  const [debouncedSearch] = useDebouncedValue(eventName, 300);

  const { data, isFetching, error, isLoading } =
    useGetScreenNameToEventQueryMapping({
      queryParams: {
        search_string: debouncedSearch,
        limit: "10",
      },
    });

  useEffect(() => {
    if (!data || !data.data || isFetching || isLoading) return;
    const eventsData = (
      data as ApiResponse<GetScreeNameToEvenQueryMappingResponse>
    ).data;
    if (!eventsData?.eventList) return;

    const match = eventsData.eventList.find(
      (e) => e?.metadata?.eventName === eventName,
    );
    if (match) {
      onEventNameSelect?.(match.properties);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, isFetching, isLoading, eventName]);

  const options = useMemo(() => {
    if (isFetching || isLoading) {
      return [
        {
          label:
            CRITICAL_INTERACTION_FORM_CONSTANTS.AUTOCOMPLETE_FETCHING_PLACEHOLDER,
          value:
            CRITICAL_INTERACTION_FORM_CONSTANTS.AUTOCOMPLETE_FETCHING_PLACEHOLDER,
          key: CRITICAL_INTERACTION_FORM_CONSTANTS.AUTOCOMPLETE_FETCHING_PLACEHOLDER,
          disabled: true,
        },
      ];
    }

    if (error) {
      return [
        {
          label:
            CRITICAL_INTERACTION_FORM_CONSTANTS.AUTOCOMPLETE_ERROR_PLACEHOLDER,
          value:
            CRITICAL_INTERACTION_FORM_CONSTANTS.AUTOCOMPLETE_ERROR_PLACEHOLDER,
          key: CRITICAL_INTERACTION_FORM_CONSTANTS.AUTOCOMPLETE_ERROR_PLACEHOLDER,
          disabled: true,
        },
      ];
    }

    if (!data || !data.data) {
      return [];
    }

    const { data: eventsData } =
      data as ApiResponse<GetScreeNameToEvenQueryMappingResponse>;

    if (
      !eventsData?.eventList ||
      !Array.isArray(eventsData.eventList) ||
      !eventsData.eventList.length
    ) {
      return [];
    }

    const eventsGroupingBasedOnScreenName: Record<string, Array<string>> = {};
    const tempEventsMetadata: EventsMetadata = {};
    const ungroupedEvents: string[] = [];

    eventsData.eventList.forEach((event) => {
      const evtName = event?.metadata?.eventName || "";
      if (evtName && !tempEventsMetadata[evtName]) {
        tempEventsMetadata[evtName] = {
          description:
            event?.metadata?.description ||
            CRITICAL_INTERACTION_FORM_CONSTANTS.NO_DESCRIPTION_MESSAGE,
          properties: event.properties,
        };
      }

      const screenNames = event?.metadata?.screenNames || [];
      if (screenNames.length === 0) {
        if (evtName && !ungroupedEvents.includes(evtName)) {
          ungroupedEvents.push(evtName);
        }
      } else {
        screenNames.forEach((screenName) => {
          if (!eventsGroupingBasedOnScreenName[screenName]) {
            eventsGroupingBasedOnScreenName[screenName] = [evtName];
          } else if (
            !eventsGroupingBasedOnScreenName[screenName].includes(evtName)
          ) {
            eventsGroupingBasedOnScreenName[screenName].push(evtName);
          }
        });
      }
    });

    eventMetadata.current = tempEventsMetadata;

    const hasGroups = Object.keys(eventsGroupingBasedOnScreenName).length > 0;

    if (!hasGroups) {
      return ungroupedEvents.map((name) => ({
        label: name,
        value: name,
      }));
    }

    const suggestions = [];

    if (ungroupedEvents.length > 0) {
      suggestions.push({
        group: "Events",
        items: ungroupedEvents,
      });
    }

    for (const item in eventsGroupingBasedOnScreenName) {
      suggestions.push({
        group: item,
        items: eventsGroupingBasedOnScreenName[item],
      });
    }

    return suggestions;
  }, [data, isFetching, isLoading, error]);

  const onChange = useCallback(
    (value: string) => {
      onEventNameChange(value);
    },
    [onEventNameChange],
  );

  const renderAutocompleteOption: AutocompleteProps["renderOption"] = ({
    option,
  }) => {
    return (
      <Group gap="sm">
        <div>
          <Text size="sm">{option.value}</Text>
          <Text size="xs" opacity={0.5}>
            {eventMetadata.current[option.value]?.description || ""}
          </Text>
        </div>
      </Group>
    );
  };

  const onOptionSubmit = useCallback(
    (selectedEventName: string) => {
      onEventNameSelect?.(
        eventMetadata.current[selectedEventName]?.properties,
      );
    },
    [onEventNameSelect],
  );

  return (
    <Autocomplete
      rightSectionWidth={100}
      size={CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE}
      radius={CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE}
      className={classes.eventSequenceItemAutocomplete}
      placeholder={placeHolderText}
      data={options}
      onChange={onChange}
      value={eventName}
      withAsterisk
      limit={100}
      onOptionSubmit={onOptionSubmit}
      renderOption={renderAutocompleteOption}
      filter={({ options }) => options}
      required
      maxDropdownHeight={300}
      label={badgeText}
    />
  );
}
