import React from "react";
import Link from "next/link";

export default function CartPage(): React.JSX.Element {
  return (
    <div>
      <h1>Cart</h1>
      <p style={{ color: "#555" }}>
        Cart is managed client-side in this demo. Add items from the{" "}
        <Link href="/products">Products</Link> page using the{" "}
        <strong>Add to Cart</strong> button on each product detail page.
      </p>
      <p style={{ marginTop: "1rem", fontSize: "0.85rem", color: "#888" }}>
        Each successful add-to-cart fires{" "}
        <code>Pulse.trackEvent("add_to_cart", &#123; product_id &#125;)</code>{" "}
        after the Server Action resolves.
        <br />
        Failed adds (e.g. out-of-stock product p3) fire{" "}
        <code>Pulse.reportException()</code>.
      </p>
    </div>
  );
}
