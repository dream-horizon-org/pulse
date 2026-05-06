/**
 * Shared nav for Pages Router demo pages.
 * Uses next/link only — no App Router hooks (usePathname etc.).
 */
import React from "react";
import Link from "next/link";
import { useRouter } from "next/router";

const PAGES_LINKS = [
  { href: "/pages-demo", label: "Home (getStaticProps)" },
  { href: "/pages-demo/shop", label: "Shop (getServerSideProps)" },
  { href: "/pages-demo/about", label: "About" },
  { href: "/pages-demo/error-demo", label: "Error Demo" },
];

export function PagesNavBar(): React.JSX.Element {
  const { pathname } = useRouter();

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
          background: "#e0f2fe",
          color: "#0369a1",
          padding: "2px 8px",
          borderRadius: "4px",
          fontWeight: "bold",
        }}
      >
        Pages Router
      </span>

      {PAGES_LINKS.map(({ href, label }) => (
        <Link
          key={href}
          href={href}
          style={{ fontWeight: pathname === href ? "bold" : "normal" }}
        >
          {label}
        </Link>
      ))}

      {/* Cross-router link — full page reload into App Router */}
      <Link
        href="/"
        style={{
          marginLeft: "auto",
          fontSize: "0.85rem",
          color: "#666",
        }}
      >
        ← App Router Demo
      </Link>
    </nav>
  );
}
