"use client";

import React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";

export function NavBar(): React.JSX.Element {
  const pathname = usePathname();

  const links = [
    { href: "/", label: "Home" },
    { href: "/products", label: "Products" },
    { href: "/cart", label: "Cart" },
    { href: "/search", label: "Search" },
    { href: "/api-demo", label: "API Demo" },
    { href: "/error-demo", label: "Error Demo" },
  ];

  return (
    <nav
      style={{
        display: "flex",
        gap: "1rem",
        padding: "1rem",
        borderBottom: "1px solid #eee",
        alignItems: "center",
        flexWrap: "wrap",
      }}
    >
      <span
        style={{
          fontSize: "0.75rem",
          background: "#f0fdf4",
          color: "#15803d",
          padding: "2px 8px",
          borderRadius: "4px",
          fontWeight: "bold",
        }}
      >
        App Router
      </span>

      {links.map(({ href, label }) => (
        <Link
          key={href}
          href={href}
          style={{ fontWeight: pathname === href ? "bold" : "normal" }}
        >
          {label}
        </Link>
      ))}

      {/* Cross-router link — full page reload into Pages Router */}
      <Link
        href="/pages-demo"
        style={{
          marginLeft: "auto",
          fontSize: "0.85rem",
          color: "#666",
        }}
      >
        Pages Router Demo →
      </Link>
    </nav>
  );
}
