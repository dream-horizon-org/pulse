import { Box } from "@mantine/core";
import type { ReactNode } from "react";
import classes from "../SessionReplayDetail.module.css";

/**
 * Scrollable tab body (Network, App Vitals, Interaction, etc.).
 * Height comes from the flex chain (tabContent → panel); no fixed max-height.
 */
export function TabPanelScrollArea({ children }: { children: ReactNode }) {
  return <Box className={classes.tabPanelScroll}>{children}</Box>;
}
