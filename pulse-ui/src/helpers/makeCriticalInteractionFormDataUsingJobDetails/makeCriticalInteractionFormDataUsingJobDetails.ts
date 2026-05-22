import { UseFormReturn } from "react-hook-form";
import { InteractionDetailsResponse } from "../getInteractionDetails";
import { CriticalInteractionFormData } from "../../screens/CriticalInteractionForm";
import {
  EventSequenceItem,
  GlobalBlacklistedEvent,
} from "../../hooks/useGetInteractionDetails/useGetInteractionDetails.interface";

export const makeCriticalInteractionFormDataUsingJobDetails = (
  jobDetailsResponse: InteractionDetailsResponse,
  formMethods: UseFormReturn<CriticalInteractionFormData>,
) => {
  formMethods.setValue("name", jobDetailsResponse.name);
  formMethods.setValue("uptimeLowerLimitInMs", jobDetailsResponse.uptimeLowerLimitInMs);
  formMethods.setValue("uptimeUpperLimitInMs", jobDetailsResponse.uptimeUpperLimitInMs);
  formMethods.setValue("uptimeMidLimitInMs", jobDetailsResponse.uptimeMidLimitInMs);
  formMethods.setValue("thresholdInMs", jobDetailsResponse.thresholdInMs);
  formMethods.setValue("description", jobDetailsResponse.description);

  const eventsGlobalBlackListingData: CriticalInteractionFormData["globalBlacklistedEvents"] =
    [];

  jobDetailsResponse.globalBlacklistedEvents.forEach(
    (item: GlobalBlacklistedEvent) => {
      eventsGlobalBlackListingData.push({
        name: item.name,
        props: item.props.map((prop) => ({
          name: prop.name,
          value: prop.value,
          operator: prop.operator || ("EQUALS" as const),
        })),
        isBlacklisted: true,
      });
    },
  );

  const eventsSequenceWhiteListingData: CriticalInteractionFormData["events"] =
    [];

  jobDetailsResponse.events.forEach((item: EventSequenceItem) => {
    eventsSequenceWhiteListingData.push({
      draggableId: crypto.randomUUID(),
      name: item.name,
      props: item.props.map((prop) => ({
        name: prop.name,
        value: prop.value,
        operator: prop.operator || ("EQUALS" as const),
      })),
      isBlacklisted: item.isBlacklisted || false,
    });
  });

  formMethods.setValue("globalBlacklistedEvents", eventsGlobalBlackListingData);
  formMethods.setValue("events", eventsSequenceWhiteListingData);
};
