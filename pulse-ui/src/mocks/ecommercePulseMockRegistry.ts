/**
 * Ecommerce demo interaction registry — same 12 names/order as
 * {@link buildEcommerceInteractionJobs} and {@link MockDataStore} when theme is on.
 */
export const ECOMMERCE_PULSE_MOCK_INTERACTION_NAMES = [
  "HomeFeedLoad",
  "CategoryListingLoad",
  "ProductDetailLoad",
  "AddToCartLineItem",
  "CartScreenLoad",
  "ApplyPromoCode",
  "CheckoutToShippingStep",
  "SaveShippingAddress",
  "PaymentAuthorize",
  "OrderConfirmationDisplay",
  "SearchToResultsLoad",
  "WishlistAddItem",
] as const;

/** Session detail “hero row” interactions: browse PLP → add to bag → pay. */
export const ECOMMERCE_SESSION_REPLAY_DETAIL_INTERACTION_ORDER = [
  "CategoryListingLoad",
  "AddToCartLineItem",
  "PaymentAuthorize",
] as const;
