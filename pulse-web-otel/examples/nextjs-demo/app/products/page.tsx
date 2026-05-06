/**
 * /products — RSC product listing.
 * Data fetched server-side; ProductCard handles client-side event tracking.
 */
import React from "react";
import { getProducts } from "../../lib/products";
import { ProductCard } from "./product-card";

export default async function ProductsPage(): Promise<React.JSX.Element> {
  const products = await getProducts();

  return (
    <div>
      <h1>Products</h1>
      <p style={{ fontSize: "0.85rem", color: "#888", marginBottom: "1rem" }}>
        {products.length} products — fetched server-side (RSC). Clicking a
        product fires <code>product_viewed</code> via{" "}
        <code>PulseWeb.trackEvent()</code>.
      </p>
      <div style={{ display: "flex", gap: "1rem", flexWrap: "wrap" }}>
        {products.map((p) => (
          <ProductCard key={p.id} product={p} source="listing" />
        ))}
      </div>
    </div>
  );
}
