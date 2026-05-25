import { UseFormReturn } from "react-hook-form";
import { CriticalInteractionFormRequestBodyParams } from "../createJob";
import { CriticalInteractionFormData } from "../../screens/CriticalInteractionForm";

export const makeCriticalInteractionFormRequestBody = (
  formMethods: UseFormReturn<CriticalInteractionFormData>,
) => {
  const eventSequenceData = formMethods.getValues("events");
  const globalBlacklistedEventsData = formMethods.getValues(
    "globalBlacklistedEvents",
  );

  const newEventSequence: CriticalInteractionFormRequestBodyParams["events"] =
    [];
  const newGlobalBlacklistedEvents: CriticalInteractionFormRequestBodyParams["globalBlacklistedEvents"] =
    [];

  eventSequenceData.forEach((sequence) => {
    const eventName = sequence.name ?? "";

    newEventSequence.push({
      name: eventName,
      props: sequence.props ?? [],
      isBlacklisted: sequence.isBlacklisted ?? false,
    });
  });

  globalBlacklistedEventsData.forEach((event) => {
    newGlobalBlacklistedEvents.push({
      name: event.name,
      props: event.props ?? [],
      isBlacklisted: true,
    });
  });

  const requestBody: CriticalInteractionFormRequestBodyParams = {
    name: formMethods.getValues("name"),
    description: formMethods.getValues("description"),
    uptimeLowerLimitInMs: parseInt(`${formMethods.getValues("uptimeLowerLimitInMs")}`),
    uptimeMidLimitInMs: parseInt(`${formMethods.getValues("uptimeMidLimitInMs")}`),
    uptimeUpperLimitInMs: parseInt(`${formMethods.getValues("uptimeUpperLimitInMs")}`),
    thresholdInMs: parseInt(`${formMethods.getValues("thresholdInMs")}`),
    events: newEventSequence,
    globalBlacklistedEvents: newGlobalBlacklistedEvents,
  };

  return requestBody;
};
