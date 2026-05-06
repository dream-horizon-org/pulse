"use client";

/**
 * Client component that calls the addToCartAction Server Action and
 * fires Pulse SDK events based on the result.
 *
 * This is the key pattern for tracking Server Action outcomes:
 *   - Success → PulseWeb.trackEvent("add_to_cart", { product_id, ... })
 *   - Failure → PulseWeb.reportException(error)
 */
import React, { useState, useTransition } from "react";
import { PulseWeb } from "@dreamhorizon/pulse-web";
import { addToCartAction } from "../../cart/actions";

export function AddToCartButton({
  productId,
  productName,
}: {
  productId: string;
  productName: string;
}): React.JSX.Element {
  const [isPending, startTransition] = useTransition();
  const [status, setStatus] = useState<"idle" | "success" | "error">("idle");
  const [message, setMessage] = useState("");

  function handleClick(): void {
    startTransition(async () => {
      setStatus("idle");
      const result = await addToCartAction(productId, productName);

      if (result.ok) {
        // Server Action succeeded — fire client-side success event
        PulseWeb.trackEvent("add_to_cart", {
          product_id: result.productId,
          product_name: result.productName,
        });
        setStatus("success");
        setMessage(`${result.productName} added to cart`);
      } else {
        // Server Action failed — report as non-fatal exception
        PulseWeb.reportException(
          new Error(`Add to cart failed: ${result.error}`),
          { product_id: result.productId },
        );
        setStatus("error");
        setMessage(result.error ?? "Unknown error");
      }
    });
  }

  return (
    <div>
      <button
        data-testid="add-to-cart-btn"
        onClick={handleClick}
        disabled={isPending}
        style={{
          padding: "0.75rem 1.5rem",
          background: isPending ? "#9ca3af" : "#1d4ed8",
          color: "#fff",
          border: "none",
          borderRadius: "6px",
          cursor: isPending ? "not-allowed" : "pointer",
          fontSize: "1rem",
        }}
      >
        {isPending ? "Adding…" : "Add to Cart"}
      </button>

      {status === "success" && (
        <p
          data-testid="cart-success"
          style={{ color: "#059669", marginTop: "0.5rem" }}
        >
          ✓ {message}
        </p>
      )}
      {status === "error" && (
        <p
          data-testid="cart-error"
          style={{ color: "#dc2626", marginTop: "0.5rem" }}
        >
          ✗ {message}
        </p>
      )}

      {status !== "idle" && (
        <p style={{ fontSize: "0.75rem", color: "#6b7280", marginTop: "0.25rem" }}>
          {status === "success"
            ? "→ trackEvent('add_to_cart') fired"
            : "→ reportException() fired"}
        </p>
      )}
    </div>
  );
}
