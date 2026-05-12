# Pricing

Plan/tier comparison page — marketing-style listing of `tiers` backed by the `tiers` backend domain.

Brief: [../../../components/pulse-ui.md](../../../components/pulse-ui.md) · Peers: [../../backend-server/domains/tiers](../../backend-server/domains/tiers.md), [organization](./organization.md).

## Purpose

Displays plan tiers (Free / Pro / Enterprise — exact set from MySQL `tiers` table) with feature matrix and CTA to contact sales or upgrade. Also used in-app as an upgrade prompt when a project hits a usage limit.

## Source location

- `pulse-ui/src/screens/Pricing/{index.ts,Pricing.tsx,Pricing.module.css}`

## Routes

`/pricing` (public), also surfaced via upgrade prompts from quota-exceeded banners.

## Data fetched

- `GET /v1/tiers` — full tier list with feature flags & limits (see [../../backend-server/domains/tiers.md](../../backend-server/domains/tiers.md)).
- When authenticated, `GET /v1/organizations/{orgId}/tier` — current plan.

## State management

TanStack Query only; no Zustand.

## Key UI components

Mantine `Card`, `Badge`, `SimpleGrid`, feature-check icons (custom `FeatureCheck` shared component).

## Notable interactions

- "Contact sales" mailto or `/support-queries` redirect.
- "Upgrade" CTA opens a billing flow (external, e.g. Stripe-hosted).
- Quota-banner mode passes `?plan=<slug>` to pre-highlight the recommended plan.

## Tests

Render test ensures every tier returned by mock API renders a card with correct limits.

## Rebuild recipe

1. Standard screen folder.
2. Query tiers + current tier.
3. Render `SimpleGrid` of `TierCard`, pass `recommended` prop from URL.
4. Guard auth-only tier data with a conditional hook.

## History / decisions

Kept marketing copy in constants so design can iterate without code churn.
