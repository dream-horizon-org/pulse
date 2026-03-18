import { ScrollArea } from "@mantine/core";
import type { ReactNode } from "react";

/** Matches EventList / tab panel scroll region height */
export const TAB_PANEL_SCROLL_MAX = "min(640px, calc(100vh - 260px))";

export function TabPanelScrollArea({ children }: { children: ReactNode }) {
  return (
    <ScrollArea
      type="scroll"
      scrollbars="y"
      style={{
        minHeight: 200,
        maxHeight: TAB_PANEL_SCROLL_MAX,
        width: "100%",
      }}
      styles={{
        root: { display: "flex", flexDirection: "column" },
        viewport: { maxHeight: TAB_PANEL_SCROLL_MAX },
      }}
    >
      {children}
    </ScrollArea>
  );
}
