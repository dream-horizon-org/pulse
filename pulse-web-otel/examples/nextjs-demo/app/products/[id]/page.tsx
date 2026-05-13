/**
 * /products/[id] — RSC product detail.
 * Server fetches product; client component handles add-to-cart Server Action
 * and fires Pulse events on success/failure.
 */
import React from "react";
import { notFound } from "next/navigation";
import { getProduct } from "../../../lib/products";
import { AddToCartButton } from "./add-to-cart-button";

export default async function ProductDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}): Promise<React.JSX.Element> {
  const { id } = await params;
  const product = await getProduct(id);

  if (!product) notFound();

  return (
    <div style={{ maxWidth: "600px" }}>
      <div style={{ fontSize: "0.8rem", color: "#6b7280", marginBottom: "0.5rem" }}>
        {product.category}
      </div>
      <h1>{product.name}</h1>
      <p style={{ color: "#555", marginBottom: "1rem" }}>{product.description}</p>

      <div
        style={{
          fontSize: "1.5rem",
          fontWeight: "bold",
          color: "#059669",
          marginBottom: "1.5rem",
        }}
      >
        ${product.price}
      </div>

      {product.inStock ? (
        <>
          <p style={{ fontSize: "0.85rem", color: "#888", marginBottom: "0.75rem" }}>
            Clicking below calls a <strong>Server Action</strong> (
            <code>addToCartAction</code>). On success, the client fires{" "}
            <code>trackEvent("add_to_cart")</code>. Product p3 always returns an
            error — fires <code>reportException()</code> instead.
          </p>
          {/* Client component — wires Server Action + Pulse tracking */}
          <AddToCartButton productId={product.id} productName={product.name} />
        </>
      ) : (
        <div
          style={{
            padding: "0.75rem",
            background: "#fef2f2",
            border: "1px solid #fecaca",
            borderRadius: "6px",
            color: "#dc2626",
          }}
        >
          Out of stock — this product will always fail the Server Action (useful
          for testing error tracking).
        </div>
      )}
    </div>
  );
}
