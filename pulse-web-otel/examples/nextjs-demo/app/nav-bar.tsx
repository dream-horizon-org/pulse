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
    { href: "/error-demo", label: "Error Demo" },
  ];

  return (
    <nav style={{ display: "flex", gap: "1rem", padding: "1rem", borderBottom: "1px solid #eee" }}>
      {links.map(({ href, label }) => (
        <Link
          key={href}
          href={href}
          style={{ fontWeight: pathname === href ? "bold" : "normal" }}
        >
          {label}
        </Link>
      ))}
    </nav>
  );
}
