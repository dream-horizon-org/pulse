import type { ReactNode } from "react";
import { Box, Paper, Tabs } from "@mantine/core";
import {
  IconHandClick,
  IconTerminal,
  IconNetwork,
  IconGauge,
  IconMapRoute,
  IconTimeline,
} from "@tabler/icons-react";
import { AllTab } from "./AllTab";
import { InteractionTab } from "./InteractionTab";
import { ConsoleTab } from "./ConsoleTab";
import { NetworkTab } from "./NetworkTab";
import { PerformanceTab } from "./PerformanceTab";
import { UserJourneyTab } from "./UserJourneyTab";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import type { FlameChartNode } from "../../SessionTimeline/utils/flameChartTransform";
import { TABS, TAB_LABELS } from "../constants/strings";
import classes from "../SessionReplayDetail.module.css";

const TAB_ICON_SIZE = 16;
const TAB_ICON_STROKE = 1.5;

function TabIcon({ children }: { children: ReactNode }) {
  return (
    <span className={classes.tabIconSlot} aria-hidden>
      {children}
    </span>
  );
}

interface SessionTabsProps {
  activeTab: string;
  sessionData: SessionDetailData;
  currentTime: number;
  isPlaying: boolean;
  scrollToTimestamp: { t0: number; t1: number } | null;
  onEventClick: (item: FlameChartNode) => void;
  networkViewMode: "text" | "graph";
  onTabChange: (value: string) => void;
  onCriticalInteractionClick: (t0: number, t1: number) => void;
  onNetworkViewModeChange: (mode: "text" | "graph") => void;
  /** When true, tabs card fills player-matched height and panel body scrolls */
  matchPlayerHeight?: boolean;
}

export function SessionTabs({
  activeTab,
  sessionData,
  currentTime,
  isPlaying,
  scrollToTimestamp,
  onEventClick,
  networkViewMode,
  onTabChange,
  onCriticalInteractionClick,
  onNetworkViewModeChange,
  matchPlayerHeight = false,
}: SessionTabsProps) {
  return (
    <Paper
      className={`${classes.allTabContainer}${
        matchPlayerHeight ? ` ${classes.allTabContainerStretch}` : ""
      }`}
    >
      <Tabs
        value={activeTab}
        onChange={(value) => onTabChange(value || TABS.ALL)}
        color="teal"
        variant="default"
        classNames={{
          root: matchPlayerHeight
            ? classes.sessionTabsRootStretch
            : classes.sessionTabsRoot,
          list: classes.sessionTabsList,
          tab: classes.sessionTab,
        }}
      >
        <Tabs.List>
          <Tabs.Tab
            value={TABS.ALL}
            leftSection={
              <TabIcon>
                <IconTimeline size={TAB_ICON_SIZE} stroke={TAB_ICON_STROKE} />
              </TabIcon>
            }
          >
            {TAB_LABELS.ALL}
          </Tabs.Tab>
          <Tabs.Tab
            value={TABS.INTERACTION}
            leftSection={
              <TabIcon>
                <IconHandClick
                  size={TAB_ICON_SIZE}
                  stroke={TAB_ICON_STROKE}
                />
              </TabIcon>
            }
          >
            {TAB_LABELS.INTERACTION}
          </Tabs.Tab>
          <Tabs.Tab
            value={TABS.NETWORK}
            leftSection={
              <TabIcon>
                <IconNetwork
                  size={TAB_ICON_SIZE}
                  stroke={TAB_ICON_STROKE}
                />
              </TabIcon>
            }
          >
            {TAB_LABELS.NETWORK}
          </Tabs.Tab>
          <Tabs.Tab
            value={TABS.PERFORMANCE}
            leftSection={
              <TabIcon>
                <IconGauge size={TAB_ICON_SIZE} stroke={TAB_ICON_STROKE} />
              </TabIcon>
            }
          >
            {TAB_LABELS.PERFORMANCE}
          </Tabs.Tab>
          <Tabs.Tab
            value={TABS.USER_JOURNEY}
            leftSection={
              <TabIcon>
                <IconMapRoute
                  size={TAB_ICON_SIZE}
                  stroke={TAB_ICON_STROKE}
                />
              </TabIcon>
            }
          >
            {TAB_LABELS.USER_JOURNEY}
          </Tabs.Tab>
          <Tabs.Tab
            value={TABS.CONSOLE}
            leftSection={
              <TabIcon>
                <IconTerminal
                  size={TAB_ICON_SIZE}
                  stroke={TAB_ICON_STROKE}
                />
              </TabIcon>
            }
          >
            {TAB_LABELS.CONSOLE}
          </Tabs.Tab>
        </Tabs.List>

        <Box
          className={`${classes.tabContent}${
            matchPlayerHeight ? ` ${classes.tabContentStretch}` : ""
          }`}
        >
          <Tabs.Panel value={TABS.ALL}>
            <AllTab
              sessionData={sessionData}
              currentTime={currentTime}
              isPlaying={isPlaying}
              scrollToTimestamp={scrollToTimestamp}
              onEventClick={onEventClick}
            />
          </Tabs.Panel>

          <Tabs.Panel value={TABS.INTERACTION}>
            <InteractionTab
              sessionData={sessionData}
              onCriticalInteractionClick={onCriticalInteractionClick}
            />
          </Tabs.Panel>

          <Tabs.Panel value={TABS.NETWORK}>
            <NetworkTab
              sessionData={sessionData}
              viewMode={networkViewMode}
              onViewModeChange={onNetworkViewModeChange}
            />
          </Tabs.Panel>

          <Tabs.Panel value={TABS.PERFORMANCE}>
            <PerformanceTab sessionData={sessionData} />
          </Tabs.Panel>

          <Tabs.Panel value={TABS.USER_JOURNEY}>
            <UserJourneyTab sessionData={sessionData} />
          </Tabs.Panel>

          <Tabs.Panel value={TABS.CONSOLE}>
            <ConsoleTab sessionData={sessionData} />
          </Tabs.Panel>
        </Box>
      </Tabs>
    </Paper>
  );
}
