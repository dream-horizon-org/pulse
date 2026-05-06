import type { Metadata } from "next";
import "./globals.css";
import { PulseProvider } from "./providers/PulseProvider";
import { QueryProvider } from "./providers/QueryProvider";
import { UserProvider } from "./context/UserContext";
import { CartProvider } from "./context/CartContext";
import { NavBar } from "./components/NavBar";
import { BottomNav } from "./components/BottomNav";
import { PulsePageView } from "./components/PulseRouterTracker";
import { PulseDebugPanel } from "./components/PulseDebugPanel";

export const metadata: Metadata = {
  title: "DreamLotto — Pulse Web SDK Demo",
  description:
    "Lottery demo app wired to the Pulse Web SDK. Every signal type covered.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <QueryProvider>
          <UserProvider>
            <CartProvider>
              <PulseProvider>
                <PulsePageView />
                <NavBar />
                <main className="max-w-2xl mx-auto px-4 pb-32 pt-4 min-h-dvh">
                  {children}
                </main>
                <BottomNav />
                <PulseDebugPanel />
              </PulseProvider>
            </CartProvider>
          </UserProvider>
        </QueryProvider>
      </body>
    </html>
  );
}
