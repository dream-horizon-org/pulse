import { PropFilter } from "../../CriticalInteractionForm.interface";
import { FilterInputFeild } from "../EventsSequenceSection";

export type GlobalBlacklistEventSequenceItemProps = {
  eventName: string;
  showAddEventFilterButton: boolean;
  filters?: Array<PropFilter>;
  onEventNameChange: (eventName: string) => void;
  onDeleteButtonClick: () => void;
  onAddEventFilterButtonClick: () => void;
  onFilterValueChange: (
    filterIndex: number,
    filterValue: string,
    inputFeildName: FilterInputFeild,
  ) => void;
  onDeleteFilterButtonClick: (filterIndex: number) => void;
};
