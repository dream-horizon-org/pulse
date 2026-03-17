import { render, screen } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import userEvent from "@testing-library/user-event";
import { SessionHeader } from "../components/SessionHeader";
import { LABELS } from "../constants/strings";

const renderWithProvider = (component: React.ReactElement) => {
  return render(<MantineProvider>{component}</MantineProvider>);
};

describe("SessionHeader", () => {
  it("renders without crashing", () => {
    const onBack = jest.fn();
    renderWithProvider(<SessionHeader onBack={onBack} />);
  });

  it("renders back button with label", () => {
    const onBack = jest.fn();
    renderWithProvider(<SessionHeader onBack={onBack} />);
    expect(
      screen.getByRole("button", { name: LABELS.BACK }),
    ).toBeInTheDocument();
  });

  it("calls onBack when back button is clicked", async () => {
    const onBack = jest.fn();
    renderWithProvider(<SessionHeader onBack={onBack} />);
    await userEvent.click(screen.getByRole("button", { name: LABELS.BACK }));
    expect(onBack).toHaveBeenCalledTimes(1);
  });
});
