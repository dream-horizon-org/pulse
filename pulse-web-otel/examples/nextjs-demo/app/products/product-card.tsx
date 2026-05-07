"use client";

import React from "react";
import Link from "next/link";
import { Pulse } from "@dreamhorizon/pulse-web";
import type { Product } from "../../lib/products";

const cardStyle: React.CSSProperties = {
  border: "1px solid #e5e7eb",
  borderRadius: "8px",
  padding: "1rem",
  width: "200px",
  cursor: "pointer",
};

export function FeaturedProductCard({
  product,
}: {
  product: Product;
}): React.JSX.Element {
  return (
    <Link
      href={`/products/${product.id}`}
      style={{ textDecoration: "none", color: "inherit" }}
      onClick={() => {
        // Client-side event fired even though product data came from the server
        Pulse.trackEvent("product_viewed", {
          product_id: product.id,
          product_name: product.name,
          product_category: product.category,
          source: "featured",
        });
      }}
    >
      <div style={cardStyle}>
        <div
          style={{
            fontSize: "0.7rem",
            color: "#6b7280",
            textTransform: "uppercase",
            marginBottom: "0.25rem",
          }}
        >
          {product.category}
        </div>
        <strong>{product.name}</strong>
        <div style={{ marginTop: "0.5rem", color: "#059669" }}>
          ${product.price}
        </div>
        {!product.inStock && (
          <div style={{ fontSize: "0.75rem", color: "#ef4444", marginTop: "0.25rem" }}>
            Out of stock
          </div>
        )}
      </div>
    </Link>
  );
}

export function ProductCard({
  product,
  source = "listing",
}: {
  product: Product;
  source?: string;
}): React.JSX.Element {
  return (
    <Link
      href={`/products/${product.id}`}
      style={{ textDecoration: "none", color: "inherit" }}
      onClick={() => {
        Pulse.trackEvent("product_viewed", {
          product_id: product.id,
          product_name: product.name,
          product_category: product.category,
          source,
        });
      }}
    >
      <div style={cardStyle}>
        <div
          style={{
            fontSize: "0.7rem",
            color: "#6b7280",
            textTransform: "uppercase",
            marginBottom: "0.25rem",
          }}
        >
          {product.category}
        </div>
        <strong>{product.name}</strong>
        <div style={{ marginTop: "0.5rem", color: "#059669" }}>
          ${product.price}
        </div>
        {!product.inStock && (
          <div style={{ fontSize: "0.75rem", color: "#ef4444", marginTop: "0.25rem" }}>
            Out of stock
          </div>
        )}
      </div>
    </Link>
  );
}
