import React from "react";

/**
 * E2E harness for CON-03 — mid-session consent revoke via Root remount.
 */
export default function ConsentLab(): React.ReactElement {
  return (
    <div style={{ padding: 24, maxWidth: 560 }}>
      <h1 style={{ fontSize: 28, fontWeight: 700, marginBottom: 12 }}>
        Consent lab
      </h1>
      <p style={{ color: "#64748b", marginBottom: 24 }}>
        Allows Playwright to revoke consent after session.start without a full
        page reload.
      </p>
      <button
        type="button"
        data-testid="pulse-remount-denied"
        onClick={() => {
          void (
            window as unknown as {
              __pulseE2eRemountDenied?: () => Promise<void>;
            }
          ).__pulseE2eRemountDenied?.();
        }}
        style={{
          background: "#dc2626",
          color: "#fff",
          border: "none",
          borderRadius: 8,
          padding: "12px 20px",
          fontWeight: 600,
          cursor: "pointer",
        }}
      >
        Revoke consent (shutdown + remount DENIED)
      </button>
    </div>
  );
}
