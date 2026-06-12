import {
  Autocomplete,
  AutocompleteProps,
  Divider,
  Group,
  Text,
  TextInput,
  Tooltip,
  Select,
  rem,
} from "@mantine/core";
import { EventsPropsFilterProps } from "./EventsPropsFilter.interface";
import classes from "./EventsPropsFilter.module.css";
import {
  CRITICAL_INTERACTION_FORM_CONSTANTS,
  TOOLTIP_LABLES,
} from "../../../../constants";

import { IconTrash } from "@tabler/icons-react";
import { useRef } from "react";
import { OPERATOR_OPTIONS } from "./EventsPropsFilter.constants";

export function EventsPropsFilter(props: EventsPropsFilterProps) {
  const {
    eventsPropertiesSuggestions,
    showAddEventFilterButton,
    filters,
    onFilterValueChange,
    onDeleteFilterButtonClick,
    onAddEventFilterButtonClick,
  } = props;

  const propertiesDescription = useRef<Record<string, string>>({});

  if (!showAddEventFilterButton) {
    return <></>;
  }

  const getOptions = () => {
    let suggestions: Array<string> = [];
    let tempPropertiesDescription: Record<string, string> = {};

    eventsPropertiesSuggestions?.forEach((property) => {
      if (!property.archived) {
        tempPropertiesDescription[property.propertyName] =
          property.description || "No description found";
        suggestions.push(property.propertyName);
      }
    });

    propertiesDescription.current = tempPropertiesDescription;
    return suggestions;
  };

  const renderAutocompleteOption: AutocompleteProps["renderOption"] = ({
    option,
  }) => {
    return (
      <Group gap="sm">
        <div>
          <Text size="sm">{option.value}</Text>
          <Text size="xs" opacity={0.5}>
            {propertiesDescription.current[option.value] || ""}
          </Text>
        </div>
      </Group>
    );
  };

  return (
    <div className={classes.eventPropsContainer}>
      <div className={classes.propsInputMainContainer}>
        {filters && filters.length > 0 && <Divider />}
        {filters?.map((filter, filterIndex) => (
          <div className={classes.propsInputContainer} key={filterIndex}>
            <Autocomplete
              placeholder="Prop name"
              onChange={(propName: string) => {
                onFilterValueChange(filterIndex, propName, "name");
              }}
              data={getOptions()}
              renderOption={renderAutocompleteOption}
              value={filter.name}
              size={CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE}
              radius={CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE}
              required
            />
            <Select
              placeholder="Operator"
              value={filter.operator || "EQUALS"}
              onChange={(operator) => {
                onFilterValueChange(
                  filterIndex,
                  operator || "EQUALS",
                  "operator",
                );
              }}
              data={OPERATOR_OPTIONS}
              size={CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE}
              radius={CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE}
              allowDeselect={false}
              style={{ minWidth: "120px" }}
              required
            />
            <TextInput
              placeholder="Prop value"
              onChange={(event: any) => {
                event.preventDefault();
                onFilterValueChange(
                  filterIndex,
                  event.target.value,
                  "value",
                );
              }}
              value={filter.value}
              size={CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE}
              radius={CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE}
              required
            />
            <Tooltip label={TOOLTIP_LABLES.REMOVE_FILTER_MESSAGE}>
              <button
                className={classes.deletePropsButton}
                onClick={(event) => {
                  event.preventDefault();
                  onDeleteFilterButtonClick(filterIndex);
                }}
              >
                <IconTrash
                  style={{ width: rem(16), height: rem(16) }}
                  stroke={1.5}
                />
              </button>
            </Tooltip>
          </div>
        ))}
        <div
          className={classes.addPropsFilterButton}
          onClick={onAddEventFilterButtonClick}
        >
          {/* <IconPlus size={18} />{" "} */}
          {CRITICAL_INTERACTION_FORM_CONSTANTS.ADD_PROPS_FILTER_BUTTON_TEXT}
        </div>
      </div>
    </div>
  );
}
