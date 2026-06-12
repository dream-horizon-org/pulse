import { API_ROUTES, API_BASE_URL } from "../../constants";
import { makeRequest } from "../makeRequest";
import { WhitelistEventsResponse } from "./whitelistEvents.interface";

export const whitelistEvents = (eventTopics: Array<string>) => {
  return makeRequest<WhitelistEventsResponse>({
    url: `${API_BASE_URL}${API_ROUTES.WHITELIST_EVENTS.apiPath}`,
    init: {
      method: API_ROUTES.WHITELIST_EVENTS.method,
      body: JSON.stringify({
        eventTopics: eventTopics,
      }),
    },
  });
};
