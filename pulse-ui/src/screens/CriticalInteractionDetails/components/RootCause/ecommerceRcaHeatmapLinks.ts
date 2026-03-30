import type { RcaHeatmapTarget } from "./rcaHeatmapLinks";

/**
 * Heatmap targets when {@link isEcommerceMockThemeEnabled} — aligns with
 * {@link buildEcommerceInteractionJobs} names; screens reuse mock Pulse registry IDs.
 */
export const ECOMMERCE_RCA_HEATMAP_BY_INTERACTION: Record<
  string,
  RcaHeatmapTarget[]
> = {
  HomeFeedLoad: [
    {
      screenName: "HomeScreen",
      label: "Home — merchandising & hero load",
    },
  ],
  CategoryListingLoad: [
    {
      screenName: "ProductListScreen",
      label: "PLP — category grid & filters",
    },
  ],
  ProductDetailLoad: [
    {
      screenName: "ProductDetailScreen",
      label: "PDP — variants, price & primary CTA",
    },
  ],
  AddToCartLineItem: [
    {
      screenName: "ProductDetailScreen",
      label: "PDP — add to bag confirmation",
    },
  ],
  CartScreenLoad: [
    { screenName: "CartScreen", label: "Cart — line items & totals" },
  ],
  ApplyPromoCode: [
    { screenName: "CartScreen", label: "Cart — promo field & repricing" },
  ],
  CheckoutToShippingStep: [
    {
      screenName: "CheckoutFormScreen",
      label: "Checkout — start through shipping step",
    },
  ],
  SaveShippingAddress: [
    {
      screenName: "CheckoutFormScreen",
      label: "Checkout — address form & validation",
    },
  ],
  PaymentAuthorize: [
    {
      screenName: "PaymentScreen",
      label: "Payment — submit through gateway",
    },
  ],
  OrderConfirmationDisplay: [
    {
      screenName: "OrderListScreen",
      label: "Orders — confirmation & order summary",
    },
  ],
  SearchToResultsLoad: [
    {
      screenName: "SearchResultsScreen",
      label: "Search — query through results grid",
    },
  ],
  WishlistAddItem: [
    {
      screenName: "WishlistScreen",
      label: "Wishlist — save for later",
    },
  ],
};
