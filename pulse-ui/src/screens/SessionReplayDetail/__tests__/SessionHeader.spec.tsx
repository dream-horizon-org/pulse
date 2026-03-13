import { render, screen } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import userEvent from "@testing-library/user-event";
import { SessionHeader } from "../components/SessionHeader";
import {
  mockSessionDataWithTechnical,
  mockSessionDataNoTechnical,
} from "../__mock__/SessionReplayDetail.mock";
import { LABELS } from "../constants/strings";

const renderWithProvider = (component: React.ReactElement) => {
  return render(<MantineProvider>{component}</MantineProvider>);
};

describe("SessionHeader", () => {
  it("renders without crashing", () => {
    const onBack = jest.fn();
    renderWithProvider(
      <SessionHeader sessionData={mockSessionDataWithTechnical} onBack={onBack} />,
    );
  });

  it("renders session id", () => {
    const onBack = jest.fn();
    renderWithProvider(
      <SessionHeader sessionData={mockSessionDataWithTechnical} onBack={onBack} />,
    );
    expect(screen.getByText("test-session-123")).toBeInTheDocument();
  });

  it("renders back button with label", () => {
    const onBack = jest.fn();
    renderWithProvider(
      <SessionHeader sessionData={mockSessionDataWithTechnical} onBack={onBack} />,
    );
    expect(screen.getByRole("button", { name: LABELS.BACK })).toBeInTheDocument();
  });

  it("calls onBack when back button is clicked", async () => {
    const onBack = jest.fn();
    renderWithProvider(
      <SessionHeader sessionData={mockSessionDataWithTechnical} onBack={onBack} />,
    );
    await userEvent.click(screen.getByRole("button", { name: LABELS.BACK }));
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it("renders quality score label", () => {
    const onBack = jest.fn();
    renderWithProvider(
      <SessionHeader sessionData={mockSessionDataWithTechnical} onBack={onBack} />,
    );
    expect(screen.getByText(LABELS.QUALITY_SCORE)).toBeInTheDocument();
  });

  it("renders quality score value", () => {
    const onBack = jest.fn();
    renderWithProvider(
      <SessionHeader sessionData={mockSessionDataWithTechnical} onBack={onBack} />,
    );
    expect(screen.getByText("7.5")).toBeInTheDocument();
  });

  it("renders user id", () => {
    const onBack = jest.fn();
    renderWithProvider(
      <SessionHeader sessionData={mockSessionDataWithTechnical} onBack={onBack} />,
    );
    expect(screen.getByText("user-456")).toBeInTheDocument();
  });

  it("renders duration with label", () => {
    const onBack = jest.fn();
    renderWithProvider(
      <SessionHeader sessionData={mockSessionDataWithTechnical} onBack={onBack} />,
    );
    expect(screen.getByText(/2m 34s/)).toBeInTheDocument();
    expect(screen.getByText(/Duration/)).toBeInTheDocument();
  });

  it("renders device and os", () => {
    const onBack = jest.fn();
    renderWithProvider(
      <SessionHeader sessionData={mockSessionDataWithTechnical} onBack={onBack} />,
    );
    expect(screen.getByText(/Chrome macOS/)).toBeInTheDocument();
  });

  it("renders with different session data", () => {
    const onBack = jest.fn();
    const customData = { ...mockSessionDataNoTechnical, sessionId: "custom-id", userId: "custom-user", duration: 60000, device: "Safari", os: "iOS" };
    renderWithProvider(
      <SessionHeader sessionData={customData} onBack={onBack} />,
    );
    expect(screen.getByText("custom-id")).toBeInTheDocument();
    expect(screen.getByText("custom-user")).toBeInTheDocument();
    expect(screen.getByText(/1m 0s/)).toBeInTheDocument();
    expect(screen.getByText(/Safari iOS/)).toBeInTheDocument();
  });
});
