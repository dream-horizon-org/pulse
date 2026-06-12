import { EventFilters } from "../../CriticalInteractionForm.interface";
import { FilterInputFeild } from "../EventsSequenceSection";

export type EventSequenceItemProps = {
  index: number;
  totalItemsLength: number;
  draggableId: string;
  isEventBlackListed: boolean;
  showAddEventFilterButton: boolean;
  eventData: EventFilters | undefined;
  onEventNameChange: (eventName: string) => void;
  onBlacklistEventButtonClick: (event: any) => void;
  onDeleteButtonClick: () => void;
  onAddEventFilterButtonClick: () => void;
  onDeleteFilterButtonClick: (filterIndex: number) => void;
  onFilterValueChange: (
    filterIndex: number,
    filterValue: string,
    inputFeildName: FilterInputFeild,
  ) => void;
};
