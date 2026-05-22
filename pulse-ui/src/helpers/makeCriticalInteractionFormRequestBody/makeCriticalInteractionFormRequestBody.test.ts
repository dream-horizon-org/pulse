import { UseFormReturn } from "react-hook-form";
import { makeCriticalInteractionFormRequestBody } from "./makeCriticalInteractionFormRequestBody";
import { CriticalInteractionFormData } from "../../screens/CriticalInteractionForm";

function makeFormMethods(
  overrides: Partial<CriticalInteractionFormData> = {},
): UseFormReturn<CriticalInteractionFormData> {
  const values: CriticalInteractionFormData = {
    name: "TestInteraction",
    description: "desc",
    uptimeLowerLimitInMs: 16,
    uptimeMidLimitInMs: 50,
    uptimeUpperLimitInMs: 100,
    thresholdInMs: 20000,
    events: [],
    globalBlacklistedEvents: [],
    ...overrides,
  };

  return {
    getValues: (field?: string) => {
      if (!field) return values;
      return (values as Record<string, unknown>)[field];
    },
  } as unknown as UseFormReturn<CriticalInteractionFormData>;
}

describe("makeCriticalInteractionFormRequestBody", () => {
  it("should use thresholdInMs from form state (5000), not the old hardcoded 20000", () => {
    const formMethods = makeFormMethods({ thresholdInMs: 5000 });
    const result = makeCriticalInteractionFormRequestBody(formMethods);
    expect(result.thresholdInMs).toBe(5000);
  });

  it("should use thresholdInMs 20000 when form state holds 20000", () => {
    const formMethods = makeFormMethods({ thresholdInMs: 20000 });
    const result = makeCriticalInteractionFormRequestBody(formMethods);
    expect(result.thresholdInMs).toBe(20000);
  });

  it("should include thresholdInMs in the returned request body", () => {
    const formMethods = makeFormMethods({ thresholdInMs: 3000 });
    const result = makeCriticalInteractionFormRequestBody(formMethods);
    expect(result).toHaveProperty("thresholdInMs", 3000);
  });
});
