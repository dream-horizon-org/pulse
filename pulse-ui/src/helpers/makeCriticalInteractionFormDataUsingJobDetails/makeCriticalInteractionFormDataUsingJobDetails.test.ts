import { UseFormReturn } from "react-hook-form";
import { makeCriticalInteractionFormDataUsingJobDetails } from "./makeCriticalInteractionFormDataUsingJobDetails";
import { CriticalInteractionFormData } from "../../screens/CriticalInteractionForm";
import { InteractionDetailsResponse } from "../getInteractionDetails";

function makeFormMethods(): UseFormReturn<CriticalInteractionFormData> {
  return {
    setValue: jest.fn(),
  } as unknown as UseFormReturn<CriticalInteractionFormData>;
}

function makeJobDetails(
  overrides: Partial<InteractionDetailsResponse> = {},
): InteractionDetailsResponse {
  return {
    name: "TestInteraction",
    description: "desc",
    id: 1,
    uptimeLowerLimitInMs: 16,
    uptimeMidLimitInMs: 50,
    uptimeUpperLimitInMs: 100,
    thresholdInMs: 20000,
    status: "RUNNING",
    events: [],
    globalBlacklistedEvents: [],
    createdAt: 0,
    createdBy: "user",
    updatedAt: 0,
    updatedBy: "user",
    ...overrides,
  };
}

describe("makeCriticalInteractionFormDataUsingJobDetails", () => {
  it("should call setValue with thresholdInMs from jobDetailsResponse", () => {
    const formMethods = makeFormMethods();
    const jobDetails = makeJobDetails({ thresholdInMs: 5000 });

    makeCriticalInteractionFormDataUsingJobDetails(jobDetails, formMethods);

    expect(formMethods.setValue).toHaveBeenCalledWith("thresholdInMs", 5000);
  });

  it("should call setValue with thresholdInMs 20000 when job has that value", () => {
    const formMethods = makeFormMethods();
    const jobDetails = makeJobDetails({ thresholdInMs: 20000 });

    makeCriticalInteractionFormDataUsingJobDetails(jobDetails, formMethods);

    expect(formMethods.setValue).toHaveBeenCalledWith("thresholdInMs", 20000);
  });
});
