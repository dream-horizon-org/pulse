/**
 * Maps fantasy-sports mock interaction names → ecommerce equivalents for session replay evidence.
 */
const LEGACY_TO_ECOMMERCE: Record<string, string> = {
  JoinContestButtonClick: "AddToCartLineItem",
  SaveTeamButtonClick: "CheckoutToShippingStep",
  PlayerSelectTap: "ProductDetailLoad",
  ContestListAPIFetch: "CategoryListingLoad",
  PaymentSubmitClick: "PaymentAuthorize",
  WalletBalanceFetch: "CartScreenLoad",
  MatchScheduleAPICall: "HomeFeedLoad",
  LeaderboardRefreshTap: "SearchToResultsLoad",
  ProfileSaveClick: "SaveShippingAddress",
  NotificationTap: "OrderConfirmationDisplay",
  FilterApplyTap: "CategoryListingLoad",
  LiveScoreRefresh: "HomeFeedLoad",
};

export function mapLegacyCriticalInteractionNamesToEcommerce(
  names: string[] | undefined,
): string[] | undefined {
  if (names == null) return names;
  return names.map((n) => LEGACY_TO_ECOMMERCE[n] ?? n);
}
