import { Tabs } from "@mantine/core";
import { useState } from "react";
import { useSearchParams, useLocation } from "react-router-dom";
import { SessionReplaySessions } from "./SessionReplaySessions";
import { SessionQualityRcaPage } from "../SessionQualityRca";

const VALID_TABS = ["sessions", "root-cause"] as const;
type SessionReplayTab = (typeof VALID_TABS)[number];

function resolveInitialTab(
  tabParam: string | null,
  pathname: string,
): SessionReplayTab {
  if (tabParam === "root-cause") return "root-cause";
  if (pathname.includes("quality-rca")) return "root-cause";
  return "sessions";
}

export function SessionReplayTabbedPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { pathname } = useLocation();

  const [activeTab, setActiveTab] = useState<SessionReplayTab>(() =>
    resolveInitialTab(searchParams.get("tab"), pathname),
  );

  function handleTabChange(tab: string | null) {
    const next: SessionReplayTab =
      tab === "root-cause" ? "root-cause" : "sessions";
    setActiveTab(next);
    setSearchParams(next === "root-cause" ? { tab: "root-cause" } : {}, {
      replace: true,
    });
  }

  return (
    <>
      <Tabs value={activeTab} onChange={handleTabChange}>
        <Tabs.List>
          <Tabs.Tab value="sessions">Sessions</Tabs.Tab>
          <Tabs.Tab value="root-cause">Root Cause</Tabs.Tab>
        </Tabs.List>
      </Tabs>

      {activeTab === "sessions" && <SessionReplaySessions />}
      {activeTab === "root-cause" && <SessionQualityRcaPage />}
    </>
  );
}
