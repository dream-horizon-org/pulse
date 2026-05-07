/**
 * /pages-demo/shop — Pages Router product listing with getServerSideProps.
 * Data fetched on every request server-side. Events fire client-side.
 */
import React from "react";
import type { GetServerSideProps } from "next";
import Link from "next/link";
import { PagesNavBar } from "../../components/pages-nav";
import { getProducts } from "../../lib/products";
import type { Product } from "../../lib/products";
import { Pulse } from "@dreamhorizon/pulse-web";

interface Props {
  products: Product[];
  fetchedAt: string;
}

export const getServerSideProps: GetServerSideProps<Props> = async () => {
  // Runs on the server — Pulse.init() is a no-op here (SSR guard).
  // Any events here would be silently dropped. Events must be fired client-side.
  const products = await getProducts();
  return {
    props: {
      products,
      fetchedAt: new Date().toISOString(),
    },
  };
};

export default function PagesDemoShop({
  products,
  fetchedAt,
}: Props): React.JSX.Element {
  return (
    <>
      <PagesNavBar />
      <main style={{ padding: "1rem" }}>
        <h1>Shop</h1>
        <p style={{ fontSize: "0.85rem", color: "#6b7280", marginBottom: "1rem" }}>
          {products.length} products via <code>getServerSideProps</code> (
          {new Date(fetchedAt).toLocaleTimeString()}). Click a product to fire{" "}
          <code>product_viewed</code> client-side.
        </p>

        <div style={{ display: "flex", gap: "1rem", flexWrap: "wrap" }}>
          {products.map((p) => (
            <Link
              key={p.id}
              href={`/pages-demo/${p.id}`}
              style={{ textDecoration: "none", color: "inherit" }}
              onClick={() => {
                // Event fires client-side even though data came from getServerSideProps
                Pulse.trackEvent("product_viewed", {
                  product_id: p.id,
                  product_name: p.name,
                  source: "pages-router-shop",
                });
              }}
            >
              <div
                style={{
                  border: "1px solid #e5e7eb",
                  borderRadius: "8px",
                  padding: "1rem",
                  width: "180px",
                }}
              >
                <div style={{ fontSize: "0.7rem", color: "#6b7280", textTransform: "uppercase" }}>
                  {p.category}
                </div>
                <strong>{p.name}</strong>
                <div style={{ color: "#059669", marginTop: "0.25rem" }}>
                  ${p.price}
                </div>
                {!p.inStock && (
                  <div style={{ fontSize: "0.75rem", color: "#ef4444" }}>
                    Out of stock
                  </div>
                )}
              </div>
            </Link>
          ))}
        </div>
      </main>
    </>
  );
}
