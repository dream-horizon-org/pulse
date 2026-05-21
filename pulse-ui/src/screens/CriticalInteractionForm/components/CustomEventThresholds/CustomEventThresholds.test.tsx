import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MantineProvider } from "@mantine/core";
import { FormProvider, useForm } from "react-hook-form";
import React from "react";
import { CustomEventThresholds } from "./CustomEventThresholds";
import { CRITICAL_INTERACTION_FORM_CONSTANTS } from "../../../../constants";
import { CriticalInteractionFormData } from "../../CriticalInteractionForm.interface";

// Escape special regex characters in the label string (e.g. parentheses in "Error threshold (ms)")
function labelRegex(label: string): RegExp {
  return new RegExp(label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
}

function Wrapper({
  defaultValues,
  children,
}: {
  defaultValues: Partial<CriticalInteractionFormData>;
  children: React.ReactNode;
}) {
  const methods = useForm<CriticalInteractionFormData>({
    defaultValues: {
      name: "",
      description: "",
      uptimeLowerLimitInMs: 16,
      uptimeMidLimitInMs: 50,
      uptimeUpperLimitInMs: 100,
      thresholdInMs: 20000,
      events: [],
      globalBlacklistedEvents: [],
      ...defaultValues,
    },
    mode: "onChange",
  });
  return (
    <MantineProvider>
      <FormProvider {...methods}>{children}</FormProvider>
    </MantineProvider>
  );
}

function renderComponent(
  defaultValues: Partial<CriticalInteractionFormData> = {},
) {
  return render(
    <Wrapper defaultValues={defaultValues}>
      <CustomEventThresholds />
    </Wrapper>,
  );
}

const THRESHOLD_LABEL = CRITICAL_INTERACTION_FORM_CONSTANTS.ERROR_THRESHOLD_LABEL;
const THRESHOLD_ERROR = CRITICAL_INTERACTION_FORM_CONSTANTS.ERROR_THRESHOLD_ERROR_MESSAGE;

function getThresholdInput() {
  return screen.getByRole("textbox", { name: labelRegex(THRESHOLD_LABEL) });
}

describe("CustomEventThresholds — thresholdInMs field", () => {
  it("should render the field with the label from CRITICAL_INTERACTION_FORM_CONSTANTS", () => {
    renderComponent();
    expect(screen.getByText(THRESHOLD_LABEL)).toBeInTheDocument();
  });

  it("should render with default value 20000 when form is initialized with that default", () => {
    renderComponent({ thresholdInMs: 20000 });
    expect((getThresholdInput() as HTMLInputElement).value).toBe("20000");
  });

  it("should show no error after entering a valid positive integer", async () => {
    renderComponent();
    const input = getThresholdInput();

    await userEvent.clear(input);
    await userEvent.type(input, "5000");

    await waitFor(() => {
      expect(screen.queryByText(THRESHOLD_ERROR)).not.toBeInTheDocument();
    });
  });

  it("should show error message when value is 0", async () => {
    renderComponent();
    const input = getThresholdInput();

    await userEvent.clear(input);
    await userEvent.type(input, "0");
    await userEvent.tab();

    await waitFor(() => {
      expect(screen.getByText(THRESHOLD_ERROR)).toBeInTheDocument();
    });
  });

  it("should show error message when value is negative", async () => {
    renderComponent();
    const input = getThresholdInput();

    await userEvent.clear(input);
    await userEvent.type(input, "-1");
    await userEvent.tab();

    await waitFor(() => {
      expect(screen.getByText(THRESHOLD_ERROR)).toBeInTheDocument();
    });
  });

  it("should show error message when field is cleared", async () => {
    renderComponent({ thresholdInMs: 5000 });
    const input = getThresholdInput();

    await userEvent.clear(input);
    await userEvent.tab();

    await waitFor(() => {
      expect(screen.getByText(THRESHOLD_ERROR)).toBeInTheDocument();
    });
  });
});
