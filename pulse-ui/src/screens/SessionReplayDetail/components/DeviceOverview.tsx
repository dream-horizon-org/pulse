import { Box } from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import classes from "./DeviceOverview.module.css";

interface DeviceOverviewProps {
  sessionData: SessionDetailData;
  compact?: boolean;
  children?: React.ReactNode; // The actual replay content
}

export function DeviceOverview({
  sessionData,
  compact,
  children,
}: DeviceOverviewProps) {
  // Determine device type from platform
  const platform = (sessionData.platform || "web").toLowerCase();
  const isIOS = platform === "ios";
  const isAndroid = platform === "android";
  const isWeb = !isIOS && !isAndroid;
  const deviceClass = compact ? classes.deviceCompact : "";

  return (
    <Box className={classes.deviceContainer}>
      {isIOS && (
        <Box className={`${classes.iosDevice} ${deviceClass}`}>
          {/* iPhone notch */}
          <Box className={classes.iosNotch} />

          {/* Status bar */}
          <Box className={classes.iosStatusBar}>
            <Box className={classes.statusBarLeft}>9:41</Box>
            <Box className={classes.statusBarRight}>
              <Box className={classes.signalBars}>●●●●</Box>
              <Box className={classes.wifiIcon}>📶</Box>
              <Box className={classes.battery}>100%</Box>
            </Box>
          </Box>

          {/* Content area */}
          <Box className={classes.deviceContent}>{children}</Box>

          {/* Home indicator */}
          <Box className={classes.iosHomeIndicator} />
        </Box>
      )}

      {isAndroid && (
        <Box className={`${classes.androidDevice} ${deviceClass}`}>
          {/* Android status bar */}
          <Box className={classes.androidStatusBar}>
            <Box className={classes.statusBarLeft}>9:41</Box>
            <Box className={classes.statusBarRight}>
              <Box className={classes.signalBars}>●●●●</Box>
              <Box className={classes.battery}>100%</Box>
            </Box>
          </Box>

          {/* Content area */}
          <Box className={classes.deviceContent}>{children}</Box>

          {/* Android navigation bar */}
          <Box className={classes.androidNavBar}>
            <Box className={classes.navButton}>◀</Box>
            <Box className={classes.navButton}>◉</Box>
            <Box className={classes.navButton}>☐</Box>
          </Box>
        </Box>
      )}

      {isWeb && (
        <Box className={`${classes.webBrowser} ${deviceClass}`}>
          {/* Browser chrome */}
          <Box className={classes.browserChrome}>
            <Box className={classes.browserControls}>
              <Box
                className={classes.browserButton}
                style={{ background: "#ff5f57" }}
              />
              <Box
                className={classes.browserButton}
                style={{ background: "#ffbd2e" }}
              />
              <Box
                className={classes.browserButton}
                style={{ background: "#28ca42" }}
              />
            </Box>
            <Box className={classes.browserAddressBar}>
              <Box className={classes.addressBarIcon}>🔒</Box>
              <Box className={classes.addressBarText}>
                {sessionData.browser || "Chrome"} •{" "}
                {sessionData.device || "Desktop"}
              </Box>
            </Box>
            <Box className={classes.browserMenu}>⋮</Box>
          </Box>

          {/* Content area */}
          <Box className={classes.deviceContent}>{children}</Box>
        </Box>
      )}
    </Box>
  );
}
