"use client";

/**
 * /search — demonstrates includeSearch: true in App Router.
 *
 * When a user types a query, the URL becomes /search?q=shoes.
 * With includeSearch: true in PulseNavigationEvents, screen.name
 * becomes "/search?q=shoes" — capturing what users search for.
 *
 * To enable search tracking, update pulse-provider.tsx:
 *   <PulseNavigationEvents includeSearch />
 */
import React, { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { PRODUCTS } from "../../lib/products";
import { ProductCard } from "../products/product-card";

export default function SearchPage(): React.JSX.Element {
  const router = useRouter();
  const searchParams = useSearchParams();
  const query = searchParams.get("q") ?? "";
  const [input, setInput] = useState(query);

  const results = query
    ? PRODUCTS.filter(
        (p) =>
          p.name.toLowerCase().includes(query.toLowerCase()) ||
          p.category.toLowerCase().includes(query.toLowerCase()),
      )
    : [];

  function handleSearch(e: React.FormEvent): void {
    e.preventDefault();
    const trimmed = input.trim();
    if (trimmed) {
      router.push(`/search?q=${encodeURIComponent(trimmed)}`);
    } else {
      router.push("/search");
    }
  }

  return (
    <div>
      <h1>Search</h1>
      <p style={{ fontSize: "0.85rem", color: "#888", marginBottom: "1rem" }}>
        With <code>{"<PulseNavigationEvents includeSearch />"}</code>, searching
        tracks <code>screen.name = /search?q=shoes</code>. Without it, only{" "}
        <code>/search</code> is tracked.
      </p>

      <form onSubmit={handleSearch} style={{ display: "flex", gap: "0.5rem", marginBottom: "1.5rem" }}>
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Search products…"
          style={{
            padding: "0.5rem 1rem",
            border: "1px solid #d1d5db",
            borderRadius: "6px",
            fontSize: "1rem",
            width: "300px",
          }}
        />
        <button
          type="submit"
          style={{
            padding: "0.5rem 1rem",
            background: "#1d4ed8",
            color: "#fff",
            border: "none",
            borderRadius: "6px",
            cursor: "pointer",
          }}
        >
          Search
        </button>
      </form>

      {query && (
        <>
          <p style={{ color: "#6b7280", marginBottom: "1rem" }}>
            {results.length} result{results.length !== 1 ? "s" : ""} for &ldquo;
            {query}&rdquo;
          </p>
          {results.length > 0 ? (
            <div style={{ display: "flex", gap: "1rem", flexWrap: "wrap" }}>
              {results.map((p) => (
                <ProductCard key={p.id} product={p} source="search" />
              ))}
            </div>
          ) : (
            <p>No products match your query.</p>
          )}
        </>
      )}
    </div>
  );
}
