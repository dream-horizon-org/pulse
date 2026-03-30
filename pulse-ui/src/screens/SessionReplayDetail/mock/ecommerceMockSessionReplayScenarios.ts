import type { SessionItem } from "../../../services/sessionReplay/types";
import { mapLegacyCriticalInteractionNamesToEcommerce } from "../../../mocks/ecommerceLegacyInteractionNameMap";

export function buildEcommerceMockSessionItemsFromDefault(
  items: SessionItem[],
): SessionItem[] {
  return items.map((s) => {
    const mapped = mapLegacyCriticalInteractionNamesToEcommerce(
      s.criticalInteractionNames,
    );
    const names = mapped != null ? [...mapped] : [];
    if (s.sessionId === "sess_mock_006") {
      names.push("ApplyPromoCode");
    }
    if (s.sessionId === "sess_mock_010") {
      names.push("WishlistAddItem");
    }
    return {
      ...s,
      criticalInteractionNames:
        names.length > 0 ? names : s.criticalInteractionNames,
    };
  });
}
