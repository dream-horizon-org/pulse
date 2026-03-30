import type { InteractionDiscoverySuggestion } from "../hooks/useGetInteractionDiscoveries/useGetInteractionDiscoveries.interface";
import { ECOMMERCE_INTERACTION_DISCOVERY_MOCK_SUGGESTIONS } from "./ecommerceInteractionDiscoveryMockData";
import { INTERACTION_DISCOVERY_MOCK_SUGGESTIONS } from "./interactionDiscoveryMockData";
import { isEcommerceMockThemeEnabled } from "./mockEcommerceTheme";

/** Mock GET /v1/interactions/discoveries — theme selected at build time via env. */
export function getInteractionDiscoveryMockSuggestions(): InteractionDiscoverySuggestion[] {
  return isEcommerceMockThemeEnabled()
    ? ECOMMERCE_INTERACTION_DISCOVERY_MOCK_SUGGESTIONS
    : INTERACTION_DISCOVERY_MOCK_SUGGESTIONS;
}
