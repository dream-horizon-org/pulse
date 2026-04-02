import { Paper, Box, Tabs } from "@mantine/core";
import {
  IconHandClick,
  IconTerminal,
  IconNetwork,
  IconGauge,
  IconRoute2,
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

interface SessionTabsProps {
  activeTab: string;
  sessionData: SessionDetailData;
  currentTime: number;
  scrollToTimestamp: { t0: number; t1: number } | null;
  onEventClick: (item: FlameChartNode) => void;
  networkViewMode: "text" | "graph";
  onTabChange: (value: string) => void;
  onCriticalInteractionClick: (t0: number, t1: number) => void;
  onNetworkViewModeChange: (mode: "text" | "graph") => void;
}

export function SessionTabs({
  activeTab,
  sessionData,
  currentTime,
  scrollToTimestamp,
  onEventClick,
  networkViewMode,
  onTabChange,
  onCriticalInteractionClick,
  onNetworkViewModeChange,
}: SessionTabsProps) {
  return (
    <Paper className={classes.allTabContainer}>
      <Tabs
        value={activeTab}
        onChange={(value) => onTabChange(value || TABS.ALL)}
        classNames={{ root: classes.sessionTabsRoot }}
      >
        <Tabs.List>
          <Tabs.Tab value={TABS.ALL}>{TAB_LABELS.ALL}</Tabs.Tab>
          <Tabs.Tab
            value={TABS.INTERACTION}
            leftSection={<IconHandClick size={14} />}
          >
            {TAB_LABELS.INTERACTION}
          </Tabs.Tab>
          <Tabs.Tab
            value={TABS.NETWORK}
            leftSection={<IconNetwork size={14} />}
          >
            {TAB_LABELS.NETWORK}
          </Tabs.Tab>
          <Tabs.Tab
            value={TABS.PERFORMANCE}
            leftSection={<IconGauge size={14} />}
          >
            {TAB_LABELS.PERFORMANCE}
          </Tabs.Tab>
          <Tabs.Tab
            value={TABS.USER_JOURNEY}
            leftSection={<IconRoute2 size={14} />}
          >
            {TAB_LABELS.USER_JOURNEY}
          </Tabs.Tab>
          <Tabs.Tab
            value={TABS.CONSOLE}
            leftSection={<IconTerminal size={14} />}
          >
            {TAB_LABELS.CONSOLE}
          </Tabs.Tab>
        </Tabs.List>

        <Box className={classes.tabContent}>
          <Tabs.Panel value={TABS.ALL}>
            <AllTab
              sessionData={sessionData}
              currentTime={currentTime}
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
            <ConsoleTab />
          </Tabs.Panel>
        </Box>
      </Tabs>
    </Paper>
  );
}
