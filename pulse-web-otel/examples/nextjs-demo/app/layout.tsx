import React, { type ReactNode } from "react";
import { PulseClientProvider } from "./pulse-provider";
import { NavBar } from "./nav-bar";

export const metadata = {
  title: "Pulse Web SDK — Next.js Demo",
};

export default function RootLayout({
  children,
}: {
  children: ReactNode;
}): React.JSX.Element {
  return (
    <html lang="en">
      <body>
        <PulseClientProvider>
          <NavBar />
          <main style={{ padding: "1rem" }}>{children}</main>
        </PulseClientProvider>
      </body>
    </html>
  );
}
