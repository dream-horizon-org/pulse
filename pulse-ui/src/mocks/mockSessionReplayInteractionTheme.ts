import { isEcommerceMockThemeEnabled } from "./mockEcommerceTheme";
import { getActiveSessionReplayDetailInteractionOrder } from "./mockPulseProjectRegistry";

export function isMockPaymentInteractionName(name: string): boolean {
  return isEcommerceMockThemeEnabled()
    ? name === "PaymentAuthorize"
    : name === "PaymentSubmitClick";
}

export function isMockPrimaryRetailTapInteractionName(name: string): boolean {
  return isEcommerceMockThemeEnabled()
    ? name === "AddToCartLineItem"
    : name === "JoinContestButtonClick";
}

/** Third slot in session detail interaction order (payment). */
export function getMockPaymentInteractionNameForDetail(): string {
  const order = getActiveSessionReplayDetailInteractionOrder();
  return order[2] ?? "PaymentSubmitClick";
}
