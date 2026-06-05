/**
 * Smoke tests for DropoffPanel.
 * Verifies that:
 *  - the drawer stays quiet when closed
 *  - the ranked cause list renders on successful fetch
 *  - an empty cause list shows the "no OTel signals" copy
 */
import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MantineProvider } from "@mantine/core";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { DropoffPanel } from "../DropoffPanel";

jest.mock("../../../services/funnels.service", () => ({
  fetchFunnelDropoff: jest.fn(),
  fetchFunnelDropoffEvidence: jest.fn(),
}));
jest.mock("../../../hooks/useProjectQueryEnabled", () => ({
  useProjectQueryEnabled: (base: boolean) => base,
}));

import { fetchFunnelDropoff } from "../../../services/funnels.service";

const mockedFetch = fetchFunnelDropoff as jest.Mock;

function withProviders(ui: React.ReactElement) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={qc}>
      <MantineProvider>{ui}</MantineProvider>
    </QueryClientProvider>,
  );
}

describe("DropoffPanel", () => {
  beforeEach(() => {
    mockedFetch.mockReset();
  });

  test("does not fetch when closed", () => {
    withProviders(
      <DropoffPanel
        opened={false}
        onClose={() => {}}
        funnelId="f-1"
        stepIndex={0}
      />,
    );
    expect(mockedFetch).not.toHaveBeenCalled();
  });

  test("renders the ranked causes when data is available", async () => {
    mockedFetch.mockResolvedValue({
      data: {
        funnelId: 1,
        stepIndex: 1,
        stepName: "Checkout",
        mode: "UNIQUE_USERS",
        dropoffCohort: 500,
        converterCohort: 800,
        causes: [
          {
            causeKind: "crash",
            causeKey: "NPE@Checkout",
            causeLabel: "NullPointerException @ Checkout",
            dropoffCohort: 500,
            dropoffAffected: 125,
            converterCohort: 800,
            converterAffected: 8,
            lift: 25.0,
            dropoffRate: 25.0,
            exampleSessionIds: ["s-1", "s-2"],
          },
        ],
      },
      error: null,
    });
    withProviders(
      <DropoffPanel opened onClose={() => {}} funnelId="f-1" stepIndex={1} />,
    );
    expect(
      await screen.findByText(/NullPointerException @ Checkout/),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Step 2 · Checkout/)).toBeInTheDocument();
  });

  test("renders the empty-state copy when no causes are returned", async () => {
    mockedFetch.mockResolvedValue({
      data: {
        funnelId: 1,
        stepIndex: 0,
        stepName: "Home",
        mode: "SESSIONS",
        dropoffCohort: 0,
        converterCohort: 0,
        causes: [],
      },
      error: null,
    });
    withProviders(
      <DropoffPanel opened onClose={() => {}} funnelId="f-1" stepIndex={0} />,
    );
    expect(
      await screen.findByText(/No OTel signals lined up/i),
    ).toBeInTheDocument();
  });

  test("closes the drawer and invokes onFullRcaClick when Full RCA report is clicked", async () => {
    const onClose = jest.fn();
    const onFullRcaClick = jest.fn();
    mockedFetch.mockResolvedValue({
      data: {
        funnelId: 1,
        stepIndex: 0,
        stepName: "MatchCardClicked",
        mode: "UNIQUE_USERS",
        dropoffCohort: 499,
        converterCohort: 40935,
        causes: [
          {
            causeKind: "anr",
            causeKey: "anr@match-list",
            causeLabel: "ANR on match list",
            dropoffCohort: 499,
            dropoffAffected: 25,
            converterCohort: 40935,
            converterAffected: 40,
            lift: 51.2,
            dropoffRate: 5.0,
            exampleSessionIds: [],
          },
        ],
      },
      error: null,
    });
    withProviders(
      <DropoffPanel
        opened
        onClose={onClose}
        funnelId="f-1"
        stepIndex={0}
        onFullRcaClick={onFullRcaClick}
      />,
    );
    await userEvent.click(
      await screen.findByRole("button", { name: /Full RCA report/i }),
    );
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onFullRcaClick).toHaveBeenCalledTimes(1);
  });
});
