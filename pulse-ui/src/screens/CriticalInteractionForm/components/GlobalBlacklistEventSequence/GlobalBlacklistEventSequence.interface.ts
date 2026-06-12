import { EventFilters } from "../../CriticalInteractionForm.interface";

export type GlobalBlacklistEventSequenceProps = {
  defaultState: Array<EventFilters>;
  onEventsGlobalBlackListingDataChange: (data: Array<EventFilters>) => void;
  onChangeInActiveState: (isStateValid: boolean) => void;
  onNextClick: () => void;
  onBackClick: () => void;
};
