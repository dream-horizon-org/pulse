import { EventListData } from "../../../../hooks/useGetScreenNameToEventQueryMapping";
import { PropFilter } from "../../CriticalInteractionForm.interface";
import { FilterInputFeild } from "../EventsSequenceSection";

export type EventsPropsFilterProps = {
  eventsPropertiesSuggestions?: EventListData["properties"];
  showAddEventFilterButton: boolean;
  filters?: Array<PropFilter>;
  onAddEventFilterButtonClick: () => void;
  onFilterValueChange: (
    filterIndex: number,
    filterValue: string,
    inputFeildName: FilterInputFeild,
  ) => void;
  onDeleteFilterButtonClick: (filterIndex: number) => void;
};
