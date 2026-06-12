import { TimeFilter } from "../../../CriticalInteractionDetails.interface";

export type QuickDateTimeFilterProps = {
  handleChangeFilter: (value: number) => void;
  active: number;
  defaultQuickTimeOptions?: Array<TimeFilter>;
};
