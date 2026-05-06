import { MantineProvider } from "@mantine/core";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { PersonalTokens } from "../PersonalTokens";
import {
  useCreateUserApiKey,
  useListUserApiKeys,
  useRevokeUserApiKey,
} from "../../../hooks/useUserApiKeys";

jest.mock("../../../hooks/useUserApiKeys", () => ({
  useListUserApiKeys: jest.fn(),
  useCreateUserApiKey: jest.fn(),
  useRevokeUserApiKey: jest.fn(),
}));


const mockUseListUserApiKeys = useListUserApiKeys as jest.Mock;
const mockUseCreateUserApiKey = useCreateUserApiKey as jest.Mock;
const mockUseRevokeUserApiKey = useRevokeUserApiKey as jest.Mock;

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={qc}>
      <MantineProvider>{children}</MantineProvider>
    </QueryClientProvider>
  );
}

const defaultKey = {
  id: 1,
  displayName: "Cursor MCP",
  keyPrefix: "pulse_mcp_abc",
  isActive: true,
  createdAt: "2025-01-01T00:00:00Z",
};

beforeEach(() => {
  mockUseListUserApiKeys.mockReturnValue({ data: [defaultKey], isLoading: false });
  mockUseCreateUserApiKey.mockReturnValue({ mutateAsync: jest.fn(), isPending: false });
  mockUseRevokeUserApiKey.mockReturnValue({ mutateAsync: jest.fn(), isPending: false });
});

afterEach(() => jest.clearAllMocks());

describe("PersonalTokens", () => {
  describe("key list", () => {
    it("renders existing keys", () => {
      render(<PersonalTokens />, { wrapper });
      expect(screen.getByText("Cursor MCP")).toBeInTheDocument();
      expect(screen.getByText(/pulse_mcp_abc/)).toBeInTheDocument();
    });

    it("shows empty state when no keys", () => {
      mockUseListUserApiKeys.mockReturnValue({ data: [], isLoading: false });
      render(<PersonalTokens />, { wrapper });
      expect(screen.getByText(/No API keys yet/)).toBeInTheDocument();
    });

    it("shows loading state", () => {
      mockUseListUserApiKeys.mockReturnValue({ data: [], isLoading: true });
      render(<PersonalTokens />, { wrapper });
      expect(screen.getByText(/Loading/)).toBeInTheDocument();
    });
  });

  describe("create flow", () => {
    it("opens create modal on Generate New Key click", async () => {
      render(<PersonalTokens />, { wrapper });
      await userEvent.click(screen.getByRole("button", { name: /Generate New Key/i }));
      expect(await screen.findByText(/Generate new API key/i)).toBeInTheDocument();
    });

    it("Generate button is disabled when name is empty", async () => {
      render(<PersonalTokens />, { wrapper });
      await userEvent.click(screen.getByRole("button", { name: /Generate New Key/i }));
      const modal = await screen.findByRole("dialog");
      expect(within(modal).getByRole("button", { name: /^Generate$/i })).toBeDisabled();
    });

    it("calls createApiKey and shows copy-once key on success", async () => {
      const rawApiKey = "pulse_mcp_supersecretkey123456789012";
      const mutateAsync = jest.fn().mockResolvedValue({
        id: 2,
        displayName: "My Key",
        rawApiKey,
        keyPrefix: "pulse_mcp_super",
        createdAt: "2025-01-02T00:00:00Z",
      });
      mockUseCreateUserApiKey.mockReturnValue({ mutateAsync, isPending: false });

      render(<PersonalTokens />, { wrapper });
      await userEvent.click(screen.getByRole("button", { name: /Generate New Key/i }));

      const modal = await screen.findByRole("dialog");
      await userEvent.type(within(modal).getByLabelText(/Key name/i), "My Key");
      await userEvent.click(within(modal).getByRole("button", { name: /^Generate$/i }));

      await waitFor(() => {
        expect(mutateAsync).toHaveBeenCalledWith("My Key");
        expect(screen.getByText(rawApiKey)).toBeInTheDocument();
        expect(screen.getByText(/copy it now/i)).toBeInTheDocument();
      });
    });

    it("closes modal and clears key on Done click", async () => {
      const rawApiKey = "pulse_mcp_supersecretkey123456789012";
      const mutateAsync = jest.fn().mockResolvedValue({
        id: 2,
        displayName: "My Key",
        rawApiKey,
        keyPrefix: "pulse_mcp_super",
        createdAt: "2025-01-02T00:00:00Z",
      });
      mockUseCreateUserApiKey.mockReturnValue({ mutateAsync, isPending: false });

      render(<PersonalTokens />, { wrapper });
      await userEvent.click(screen.getByRole("button", { name: /Generate New Key/i }));
      const modal = await screen.findByRole("dialog");
      await userEvent.type(within(modal).getByLabelText(/Key name/i), "My Key");
      await userEvent.click(within(modal).getByRole("button", { name: /^Generate$/i }));
      await waitFor(() => screen.getByRole("button", { name: /^Done$/i }));
      await userEvent.click(screen.getByRole("button", { name: /^Done$/i }));

      await waitFor(() => {
        expect(screen.queryByText(rawApiKey)).not.toBeInTheDocument();
      });
    });
  });

  describe("revoke flow", () => {
    it("opens revoke confirmation when trash icon is clicked", async () => {
      render(<PersonalTokens />, { wrapper });
      // The ActionIcon (trash) is the last button in the row; get it by its red color class
      const trashButton = screen.getAllByRole("button").find(
        (btn) => btn.classList.contains("mantine-ActionIcon-root")
      );
      expect(trashButton).toBeDefined();
      await userEvent.click(trashButton!);
      expect(await screen.findByText(/Are you sure\?/i)).toBeInTheDocument();
    });

    it("calls revokeApiKey with correct id on confirm", async () => {
      const mutateAsync = jest.fn().mockResolvedValue(undefined);
      mockUseRevokeUserApiKey.mockReturnValue({ mutateAsync, isPending: false });

      render(<PersonalTokens />, { wrapper });
      const trashButton = screen.getAllByRole("button").find(
        (btn) => btn.classList.contains("mantine-ActionIcon-root")
      );
      await userEvent.click(trashButton!);
      const revokeBtn = await screen.findByRole("button", { name: /^Revoke$/i });
      await userEvent.click(revokeBtn);

      await waitFor(() => {
        expect(mutateAsync).toHaveBeenCalledWith(1);
      });
    });

    it("does not call revokeApiKey when Cancel is clicked", async () => {
      const mutateAsync = jest.fn();
      mockUseRevokeUserApiKey.mockReturnValue({ mutateAsync, isPending: false });

      render(<PersonalTokens />, { wrapper });
      const trashButton = screen.getAllByRole("button").find(
        (btn) => btn.classList.contains("mantine-ActionIcon-root")
      );
      await userEvent.click(trashButton!);
      await screen.findByRole("button", { name: /^Revoke$/i });
      await userEvent.click(screen.getByRole("button", { name: /^Cancel$/i }));

      await waitFor(() => {
        expect(mutateAsync).not.toHaveBeenCalled();
        expect(screen.queryByText(/Are you sure\?/i)).not.toBeInTheDocument();
      });
    });
  });
});
