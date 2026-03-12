import { render, screen } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { TechnicalTab } from "../components/TechnicalTab";
import {
  mockSessionDataWithTechnical,
  mockSessionDataNoTechnical,
  mockDetectedIssues,
} from "../__mock__/SessionReplayDetail.mock";
import { MESSAGES } from "../constants/strings";
import { HEADERS } from "../constants/strings";

const renderWithProvider = (component: React.ReactElement) => {
  return render(<MantineProvider>{component}</MantineProvider>);
};

describe("TechnicalTab", () => {
  it("renders without crashing when technical context is present", () => {
    renderWithProvider(
      <TechnicalTab
        sessionData={mockSessionDataWithTechnical}
        detectedIssues={mockDetectedIssues}
      />,
    );
  });

  it("renders no technical context message when technicalContext is missing", () => {
    renderWithProvider(
      <TechnicalTab
        sessionData={mockSessionDataNoTechnical}
        detectedIssues={[]}
      />,
    );
    expect(
      screen.getByText(MESSAGES.NO_TECHNICAL_CONTEXT),
    ).toBeInTheDocument();
  });

  it("renders root cause section when technical context has rootCause", () => {
    renderWithProvider(
      <TechnicalTab
        sessionData={mockSessionDataWithTechnical}
        detectedIssues={mockDetectedIssues}
      />,
    );
    expect(screen.getByText(HEADERS.ROOT_CAUSE_ANALYSIS)).toBeInTheDocument();
  });

  it("renders code references section when codeReferences exist", () => {
    renderWithProvider(
      <TechnicalTab
        sessionData={mockSessionDataWithTechnical}
        detectedIssues={mockDetectedIssues}
      />,
    );
    expect(screen.getByText(HEADERS.CODE_REFERENCES)).toBeInTheDocument();
  });

  it("renders error group info section when errorGroupInfo exists", () => {
    renderWithProvider(
      <TechnicalTab
        sessionData={mockSessionDataWithTechnical}
        detectedIssues={mockDetectedIssues}
      />,
    );
    expect(screen.getByText(HEADERS.ERROR_GROUP_INFO)).toBeInTheDocument();
  });

  it("renders related issues and PRs section", () => {
    renderWithProvider(
      <TechnicalTab
        sessionData={mockSessionDataWithTechnical}
        detectedIssues={mockDetectedIssues}
      />,
    );
    expect(screen.getByText(HEADERS.RELATED_ISSUES_PRS)).toBeInTheDocument();
  });

  it("renders reproducibility section", () => {
    renderWithProvider(
      <TechnicalTab
        sessionData={mockSessionDataWithTechnical}
        detectedIssues={mockDetectedIssues}
      />,
    );
    expect(screen.getByText(HEADERS.REPRODUCIBILITY)).toBeInTheDocument();
  });

  it("renders environment info section", () => {
    renderWithProvider(
      <TechnicalTab
        sessionData={mockSessionDataWithTechnical}
        detectedIssues={mockDetectedIssues}
      />,
    );
    expect(screen.getByText(HEADERS.ENVIRONMENT_INFO)).toBeInTheDocument();
  });
});
