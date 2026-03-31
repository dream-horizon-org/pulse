import { useMutation } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../../constants";
import { makeRequest } from "../../../helpers/makeRequest";
import { EventDefinition } from "../EventCatalog.types";

type CreateEventDefinitionPayload = {
  eventName: string;
  displayName?: string;
  description?: string;
  category?: string;
  attributes?: Array<{
    attributeName: string;
    description?: string;
    dataType?: string;
    isRequired?: boolean;
  }>;
};

export const useCreateEventDefinition = () => {
  const route = API_ROUTES.CREATE_EVENT_DEFINITION;

  return useMutation({
    mutationKey: [route.key],
    mutationFn: async (payload: CreateEventDefinitionPayload) => {
      return makeRequest<EventDefinition>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
          body: JSON.stringify(payload),
          headers: { "Content-Type": "application/json" },
        },
      });
    },
  });
};
