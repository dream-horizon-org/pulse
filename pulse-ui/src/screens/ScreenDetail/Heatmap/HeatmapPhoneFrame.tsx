import type { ReactNode } from "react";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapPhoneFrameProps {
  children: ReactNode;
}

export function HeatmapPhoneFrame({ children }: HeatmapPhoneFrameProps) {
  return (
    <div className={classes.phoneFrame}>
      <div className={classes.phoneInner}>{children}</div>
    </div>
  );
}
