import {
  EventSequenceItem,
  GlobalBlacklistedEvent,
} from "../../../../../../hooks/useGetInteractionDetails/useGetInteractionDetails.interface";

export type InteractionEventSequenceProps = {
  eventSequence: EventSequenceItem[];
  globalBlacklistedEvents: GlobalBlacklistedEvent[];
  lineVariant: "solid" | "dashed" | "dotted";
  lineWidth?: number;
};
