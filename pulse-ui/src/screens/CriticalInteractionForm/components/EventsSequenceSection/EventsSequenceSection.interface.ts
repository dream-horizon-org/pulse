import { EventSequenceData } from "../../CriticalInteractionForm.interface";

export type EventsSequenceSectionProps = {
  defaultState: Array<EventSequenceData>;
  onEventsSequenceWhitelistingDataChange: (
    data: Array<EventSequenceData>,
  ) => void;
  onChangeInActiveState: (isStateValid: boolean) => void;
  onNextClick: () => void;
  onBackClick: () => void;
};

export type FilterInputFeild = "name" | "value" | "operator";
