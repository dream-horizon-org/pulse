/**
 * Home — React Server Component.
 * Fetches featured products on the server; client component handles
 * user interactions + Pulse event tracking.
 */
import React from "react";
import Link from "next/link";
import { getFeaturedProducts } from "../lib/products";
import { FeaturedProductCard } from "./products/product-card";

export default async function HomePage(): Promise<React.JSX.Element> {
  // Data fetched on the server — no client JS needed for the initial render.
  const featured = await getFeaturedProducts();

  return (
    <div>
      <h1>Pulse Web SDK — Next.js Demo</h1>
      <p style={{ color: "#555", marginBottom: "1.5rem" }}>
        Demonstrates Pulse SDK integration with Next.js App Router (RSC, Server
        Actions, search params) and Pages Router (getServerSideProps, router
        events). Open DevTools → Network to see OTLP signals flowing.
      </p>

      <h2>Featured Products</h2>
      <p style={{ fontSize: "0.85rem", color: "#888", marginBottom: "1rem" }}>
        Fetched server-side via RSC. Click a product to fire a{" "}
        <code>product_viewed</code> event client-side.
      </p>
      <div style={{ display: "flex", gap: "1rem", flexWrap: "wrap" }}>
        {featured.map((p) => (
          <FeaturedProductCard key={p.id} product={p} />
        ))}
      </div>

      <div style={{ marginTop: "2rem", display: "flex", gap: "1rem" }}>
        <Link href="/products">Browse all products →</Link>
        <Link href="/search">Search with query params →</Link>
        <Link href="/api-demo">API call tracking demo →</Link>
      </div>
    </div>
  );
}
