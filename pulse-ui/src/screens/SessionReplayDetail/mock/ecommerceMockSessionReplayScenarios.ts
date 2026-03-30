import type { SessionItem } from "../../../services/sessionReplay/types";
import { mapLegacyCriticalInteractionNamesToEcommerce } from "../../../mocks/ecommerceLegacyInteractionNameMap";

export function buildEcommerceMockSessionItemsFromDefault(
  items: SessionItem[],
): SessionItem[] {
  return items.map((s) => ({
    ...s,
    criticalInteractionNames: mapLegacyCriticalInteractionNamesToEcommerce(
      s.criticalInteractionNames,
    ),
  }));
}
