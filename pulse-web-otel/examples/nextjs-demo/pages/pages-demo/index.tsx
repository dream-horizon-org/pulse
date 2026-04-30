/**
 * /pages-demo — Pages Router home with getStaticProps.
 * Data is fetched at build time. Events still fire client-side on interaction.
 */
import React from "react";
import type { GetStaticProps } from "next";
import Link from "next/link";
import { PagesNavBar } from "../_pages-nav";
import { PRODUCTS } from "../../lib/products";

interface Props {
  totalProducts: number;
  categories: string[];
  builtAt: string;
}

export const getStaticProps: GetStaticProps<Props> = async () => {
  const categories = [...new Set(PRODUCTS.map((p) => p.category))];
  return {
    props: {
      totalProducts: PRODUCTS.length,
      categories,
      builtAt: new Date().toISOString(),
    },
    revalidate: 60,
  };
};

export default function PagesDemoHome({
  totalProducts,
  categories,
  builtAt,
}: Props): React.JSX.Element {
  return (
    <>
      <PagesNavBar />
      <main style={{ padding: "1rem" }}>
        <h1>Pages Router Demo</h1>
        <p style={{ color: "#555", marginBottom: "1rem" }}>
          This section uses <strong>Next.js Pages Router</strong>. Screen
          tracking is via{" "}
          <code>router.events.on("routeChangeComplete")</code> in{" "}
          <code>pages/_app.tsx</code>.
        </p>

        <div
          style={{
            padding: "1rem",
            background: "#f8fafc",
            border: "1px solid #e2e8f0",
            borderRadius: "6px",
            marginBottom: "1.5rem",
            fontSize: "0.9rem",
          }}
        >
          <strong>getStaticProps data</strong> (built at{" "}
          {new Date(builtAt).toLocaleTimeString()}):
          <ul style={{ marginTop: "0.5rem", lineHeight: "1.8" }}>
            <li>{totalProducts} products in catalogue</li>
            <li>Categories: {categories.join(", ")}</li>
          </ul>
          <p style={{ color: "#6b7280", fontSize: "0.8rem", marginTop: "0.5rem" }}>
            Note: data is static (build-time). Events are still client-side.
          </p>
        </div>

        <div style={{ display: "flex", gap: "1rem" }}>
          <Link href="/pages-demo/shop">Browse Shop (getServerSideProps) →</Link>
        </div>
      </main>
    </>
  );
}
