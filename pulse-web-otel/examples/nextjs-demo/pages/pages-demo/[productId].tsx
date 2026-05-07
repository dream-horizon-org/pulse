/**
 * /pages-demo/[productId] — dynamic Pages Router product detail.
 * getServerSideProps fetches product by ID; client tracks view event.
 */
import React, { useEffect } from "react";
import type { GetServerSideProps } from "next";
import { PagesNavBar } from "../../components/pages-nav";
import { getProduct } from "../../lib/products";
import type { Product } from "../../lib/products";
import { PulseWeb } from "@dreamhorizon/pulse-web";

interface Props {
  product: Product;
}

export const getServerSideProps: GetServerSideProps<Props> = async (ctx) => {
  const id = ctx.params?.["productId"] as string;
  const product = await getProduct(id);
  if (!product) return { notFound: true };
  return { props: { product } };
};

export default function PagesDemoProductDetail({
  product,
}: Props): React.JSX.Element {
  // Fire product_detail_viewed once on mount (after hydration)
  useEffect(() => {
    PulseWeb.trackEvent("product_detail_viewed", {
      product_id: product.id,
      product_name: product.name,
      router: "pages",
    });
  }, [product.id, product.name]);

  return (
    <>
      <PagesNavBar />
      <main style={{ padding: "1rem", maxWidth: "600px" }}>
        <div style={{ fontSize: "0.8rem", color: "#6b7280", marginBottom: "0.5rem" }}>
          {product.category}
        </div>
        <h1>{product.name}</h1>
        <p style={{ color: "#555", marginBottom: "1rem" }}>{product.description}</p>
        <div style={{ fontSize: "1.5rem", fontWeight: "bold", color: "#059669", marginBottom: "1.5rem" }}>
          ${product.price}
        </div>

        <div
          style={{
            padding: "0.75rem",
            background: "#f8fafc",
            border: "1px solid #e2e8f0",
            borderRadius: "6px",
            fontSize: "0.85rem",
            color: "#6b7280",
          }}
        >
          <code>useEffect</code> on mount fires{" "}
          <code>trackEvent("product_detail_viewed")</code> — demonstrates
          tracking after <code>getServerSideProps</code> hydration.
        </div>
      </main>
    </>
  );
}
