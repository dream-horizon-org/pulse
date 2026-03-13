import { useMutation } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../../constants";
import { makeRequest } from "../../../helpers/makeRequest";
import { EventDefinition } from "../EventCatalog.types";

type UpdateEventDefinitionPayload = {
  id: number;
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

export const useUpdateEventDefinition = () => {
  const route = API_ROUTES.UPDATE_EVENT_DEFINITION;

  return useMutation({
    mutationKey: [route.key],
    mutationFn: async (payload: UpdateEventDefinitionPayload) => {
      return makeRequest<EventDefinition>({
        url: `${API_BASE_URL}${route.apiPath}/${payload.id}`,
        init: {
          method: route.method,
          body: JSON.stringify(payload),
          headers: { "Content-Type": "application/json" },
        },
      });
    },
  });
};
