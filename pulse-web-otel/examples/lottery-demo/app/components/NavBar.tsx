"use client";

import Link from "next/link";
import { useUser } from "../context/UserContext";

export function NavBar() {
  const { user, clearUser } = useUser();

  return (
    <header className="sticky top-0 z-40 bg-sapphire border-b border-sapphire-light shadow-sm">
      <div className="max-w-2xl mx-auto px-4 h-14 flex items-center justify-between">
        <Link href="/" className="flex items-center gap-2">
          <span className="text-gold font-extrabold text-xl tracking-tight">
            🎟 DreamLotto
          </span>
        </Link>

        <div className="flex items-center gap-3">
          {user ? (
            <>
              <span className="text-xs text-gold/80 hidden sm:block">
                ₹{user.walletBalance.toLocaleString("en-IN")}
              </span>
              <button
                onClick={clearUser}
                className="text-xs text-white/60 hover:text-white transition-colors"
              >
                Logout
              </button>
            </>
          ) : (
            <Link
              href="/login"
              className="text-sm font-semibold text-gold hover:text-gold/80 transition-colors"
            >
              Login
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
