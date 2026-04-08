import { Box } from "@mantine/core";
import type { ReactNode } from "react";
import classes from "../SessionReplayDetail.module.css";

/**
 * Scrollable tab body (Network, App Vitals, Events, etc.).
 * Native overflow — Mantine ScrollArea often ignores max-height with flex parents.
 */
export const TAB_PANEL_SCROLL_MAX = "min(520px, calc(100vh - 300px))";

export function TabPanelScrollArea({ children }: { children: ReactNode }) {
  return (
    <Box
      className={classes.tabPanelScroll}
      style={{
        maxHeight: TAB_PANEL_SCROLL_MAX,
      }}
    >
      {children}
    </Box>
  );
}
