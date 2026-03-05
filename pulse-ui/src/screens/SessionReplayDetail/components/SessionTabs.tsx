import { Paper, Box, Tabs } from "@mantine/core";
import {
  IconList,
  IconTerminal,
  IconNetwork,
  IconGauge,
} from "@tabler/icons-react";
import { AllTab } from "./AllTab";
import { EventsTab } from "./EventsTab";
import { ConsoleTab } from "./ConsoleTab";
import { NetworkTab } from "./NetworkTab";
import { PerformanceTab } from "./PerformanceTab";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { TABS, TAB_LABELS } from "../constants/strings";
import classes from "../SessionReplayDetail.module.css";

interface SessionTabsProps {
  activeTab: string;
  sessionData: SessionDetailData;
  eventsViewMode: "text" | "graph";
  consoleViewMode: "text" | "graph";
  networkViewMode: "text" | "graph";
  performanceViewMode: "text" | "graph";
  onTabChange: (value: string) => void;
  onCriticalInteractionClick: (t0: number, t1: number) => void;
  onEventsViewModeChange: (mode: "text" | "graph") => void;
  onConsoleViewModeChange: (mode: "text" | "graph") => void;
  onNetworkViewModeChange: (mode: "text" | "graph") => void;
  onPerformanceViewModeChange: (mode: "text" | "graph") => void;
}

export function SessionTabs({
  activeTab,
  sessionData,
  eventsViewMode,
  consoleViewMode,
  networkViewMode,
  performanceViewMode,
  onTabChange,
  onCriticalInteractionClick,
  onEventsViewModeChange,
  onConsoleViewModeChange,
  onNetworkViewModeChange,
  onPerformanceViewModeChange,
}: SessionTabsProps) {
  return (
    <Paper className={classes.allTabContainer}>
      <Tabs value={activeTab} onChange={(value) => onTabChange(value || TABS.ALL)}>
        <Tabs.List>
          <Tabs.Tab value={TABS.ALL}>{TAB_LABELS.ALL}</Tabs.Tab>
          <Tabs.Tab value={TABS.EVENTS} leftSection={<IconList size={14} />}>
            {TAB_LABELS.EVENTS}
          </Tabs.Tab>
          <Tabs.Tab
            value={TABS.CONSOLE}
            leftSection={<IconTerminal size={14} />}
          >
            {TAB_LABELS.CONSOLE}
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
        </Tabs.List>

        <Box className={classes.tabContent}>
          <Tabs.Panel value={TABS.ALL}>
            <AllTab
              sessionData={sessionData}
              onCriticalInteractionClick={onCriticalInteractionClick}
            />
          </Tabs.Panel>

          <Tabs.Panel value={TABS.EVENTS}>
            <EventsTab
              sessionData={sessionData}
              viewMode={eventsViewMode}
              onViewModeChange={onEventsViewModeChange}
            />
          </Tabs.Panel>

          <Tabs.Panel value={TABS.CONSOLE}>
            <ConsoleTab
              sessionData={sessionData}
              viewMode={consoleViewMode}
              onViewModeChange={onConsoleViewModeChange}
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
            <PerformanceTab
              sessionData={sessionData}
              viewMode={performanceViewMode}
              onViewModeChange={onPerformanceViewModeChange}
            />
          </Tabs.Panel>
        </Box>
      </Tabs>
    </Paper>
  );
}
