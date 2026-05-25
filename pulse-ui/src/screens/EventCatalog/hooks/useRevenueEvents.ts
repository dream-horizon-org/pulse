import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createRevenueEvent,
  deleteRevenueEvent,
  listRevenueEvents,
  updateRevenueEvent,
  type CreateRevenueEventRequestBody,
} from "../../../services/revenueEvents.service";
import type { RevenueEventConfig } from "../RevenueEvent.types";

export const revenueEventsQueryKey = (projectId: string) =>
  ["revenueEvents", projectId] as const;

export function useRevenueEventsList(projectId: string | undefined) {
  return useQuery({
    queryKey: revenueEventsQueryKey(projectId ?? ""),
    queryFn: async () => {
      const response = await listRevenueEvents();
      if (response.error) {
        throw new Error(response.error.message || "Failed to load revenue events");
      }
      return response.data?.revenueEvents ?? [];
    },
    enabled: !!projectId,
  });
}

export function useCreateRevenueEvent(projectId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateRevenueEventRequestBody) => createRevenueEvent(payload),
    onSuccess: () => {
      if (projectId) {
        queryClient.invalidateQueries({ queryKey: revenueEventsQueryKey(projectId) });
      }
    },
  });
}

export function useUpdateRevenueEvent(projectId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      payload,
    }: {
      id: string;
      payload: CreateRevenueEventRequestBody;
    }) => updateRevenueEvent(id, payload),
    onSuccess: () => {
      if (projectId) {
        queryClient.invalidateQueries({ queryKey: revenueEventsQueryKey(projectId) });
      }
    },
  });
}

export function useDeleteRevenueEvent(projectId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteRevenueEvent(id),
    onSuccess: () => {
      if (projectId) {
        queryClient.invalidateQueries({ queryKey: revenueEventsQueryKey(projectId) });
      }
    },
  });
}

export type SaveRevenueEventInput = Omit<
  RevenueEventConfig,
  "id" | "configuredAt"
> & { id?: string };

export function useRevenueEvents(projectId: string | undefined) {
  const listQuery = useRevenueEventsList(projectId);
  const createMutation = useCreateRevenueEvent(projectId);
  const updateMutation = useUpdateRevenueEvent(projectId);
  const deleteMutation = useDeleteRevenueEvent(projectId);

  const saveConfig = async (config: SaveRevenueEventInput): Promise<void> => {
    const payload: CreateRevenueEventRequestBody = {
      eventName: config.eventName,
      valueAttribute: config.valueAttribute,
      currency: config.currency,
      currencyAttribute: config.currencyAttribute,
      conversionWindowHours: config.conversionWindowHours,
    };

    if (config.id) {
      const response = await updateMutation.mutateAsync({ id: config.id, payload });
      if (response.error) {
        throw new Error(response.error.message || "Failed to update revenue event");
      }
      return;
    }

    const response = await createMutation.mutateAsync(payload);
    if (response.error) {
      throw new Error(response.error.message || "Failed to create revenue event");
    }
  };

  const removeConfig = async (id: string): Promise<void> => {
    const response = await deleteMutation.mutateAsync(id);
    if (response.error) {
      throw new Error(response.error.message || "Failed to delete revenue event");
    }
  };

  const configs = listQuery.data ?? [];

  const getByEventName = (eventName: string) =>
    configs.find((c) => c.eventName === eventName);

  return {
    configs,
    isLoading: listQuery.isLoading,
    isSaving: createMutation.isPending || updateMutation.isPending,
    isDeleting: deleteMutation.isPending,
    error: listQuery.error,
    saveConfig,
    removeConfig,
    getByEventName,
    refetch: listQuery.refetch,
  };
}
