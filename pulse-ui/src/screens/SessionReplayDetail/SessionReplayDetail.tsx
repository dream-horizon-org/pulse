import { useParams, useNavigate, useLocation } from "react-router-dom";
import { Box, Stack, Loader, Text, Center } from "@mantine/core";
import { DetailsSidebar } from "../SessionTimeline/components/DetailsSidebar";
import {
  FlameChartNode,
  transformToFlameChart,
} from "../SessionTimeline/utils/flameChartTransform";
import {
  getMockSessionDetail,
  PersonaType,
} from "../../services/sessionReplay/mockSessionDetail";
import { getEmptySessionDetail } from "./adapters/sessionDetailApiToData";

import { SessionHeader } from "./components/SessionHeader";
import { SessionSummary } from "./components/SessionSummary";
import { SessionTabs } from "./components/SessionTabs";
import { SessionPlayerSection } from "./components/SessionPlayerSection";
import { DEFAULTS } from "./constants/strings";
import { useSessionDetail } from "./hooks/useSessionDetail";
import { useSessionReplaySnapshots } from "./hooks/useSessionReplaySnapshots";
import classes from "./SessionReplayDetail.module.css";
import { useState, useMemo, useCallback } from "react";

export const SessionReplayDetail: React.FC = () => {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [activeTab, setActiveTab] = useState<string>("all");
  const [selectedSpan, setSelectedSpan] = useState<FlameChartNode | null>(null);
  const [scrollToTimestamp, setScrollToTimestamp] = useState<{
    t0: number;
    t1: number;
  } | null>(null);

  const [networkViewMode, setNetworkViewMode] = useState<"text" | "graph">(
    "text",
  );

  const [activePersona] = useState<PersonaType>("all");

  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);

  const { data: apiSessionData, isLoading: sessionLoading } = useSessionDetail({
    sessionId: sessionId ?? undefined,
    includeEvents: true,
    enabled: !!sessionId,
  });

  const sessionData = useMemo(() => {
    if (apiSessionData) return apiSessionData;
    const id = sessionId || DEFAULTS.SESSION_ID_UNKNOWN;
    if (process.env.REACT_APP_USE_MOCK_SESSION_REPLAY === "true") {
      return getMockSessionDetail(id);
    }
    return getEmptySessionDetail(id);
  }, [apiSessionData, sessionId]);

  const {
    images: snapshotImages,
    loading: snapshotLoading,
    snapshotDurationMs,
  } = useSessionReplaySnapshots({
    sessionId: sessionId ?? undefined,
    currentTime,
    enabled: !!sessionId,
  });

  const replayImages = snapshotImages;
  const imagesLoading = snapshotLoading;

  const effectiveDuration =
    snapshotDurationMs > 0 ? snapshotDurationMs : sessionData.duration;

  useMemo(() => {
    return transformToFlameChart(
      sessionData.traces,
      sessionData.logs,
      sessionData.exceptions,
    );
  }, [sessionData]);

  const handleBack = () => {
    const projectMatch = pathname.match(/^\/projects\/([^/]+)/);
    const base = projectMatch
      ? `/projects/${projectMatch[1]}/session-replay/sessions`
      : "/session-replay/sessions";
    navigate(base);
  };

  const handleSpanClick = (item: FlameChartNode) => {
    setSelectedSpan(item);
    setCurrentTime(item.start);
  };

  const handleCloseSidebar = () => {
    setSelectedSpan(null);
  };

  const handleCriticalInteractionClick = (t0: number, t1: number) => {
    const rawEventsSection = document.querySelector(
      "[data-raw-events-section]",
    );
    if (rawEventsSection) {
      rawEventsSection.scrollIntoView({ behavior: "smooth", block: "start" });
    }

    setScrollToTimestamp(null);
    setTimeout(() => {
      setScrollToTimestamp({ t0, t1 });
      console.log("Scroll timestamp set", { t0, t1 });
    }, 500);
  };

  const handlePlayPause = useCallback(() => {
    setIsPlaying((prev) => {
      if (!prev) {
        // Starting playback — restart from 0 if we're at (or past) the end
        setCurrentTime((ct) => (ct >= effectiveDuration ? 0 : ct));
      }
      return !prev;
    });
  }, [effectiveDuration]);

  const handleTimelineChange = useCallback((value: number) => {
    setCurrentTime(value);
  }, []);

  const handleTimeUpdate = useCallback(
    (time: number) => {
      if (time <= effectiveDuration) {
        setCurrentTime(time);
      } else {
        setIsPlaying(false);
        setCurrentTime(effectiveDuration);
      }
    },
    [effectiveDuration],
  );

  const handleSpeedChange = useCallback((speed: number) => {
    setPlaybackSpeed(speed);
  }, []);

  if (sessionLoading && !apiSessionData) {
    return (
      <Center className={classes.container} style={{ minHeight: 400 }}>
        <Stack align="center" gap="md">
          <Loader color="teal" size="lg" />
          <Text size="sm" c="dimmed">
            Loading session...
          </Text>
        </Stack>
      </Center>
    );
  }

  return (
    <Box className={classes.container}>
      <SessionHeader onBack={handleBack} />

      {activePersona === "all" && (
        <>
          <Box className={classes.summarySection}>
            <SessionSummary sessionData={sessionData} />
          </Box>
          <Box className={classes.playerSectionSplit}>
            <Box className={classes.playerSectionLeft}>
              <SessionPlayerSection
                sessionData={sessionData}
                images={replayImages}
                imagesLoading={imagesLoading}
                currentTime={currentTime}
                isPlaying={isPlaying}
                playbackSpeed={playbackSpeed}
                selectedSpan={selectedSpan}
                compact
                duration={effectiveDuration}
                onTimeUpdate={handleTimeUpdate}
                onTimelineChange={handleTimelineChange}
                onPlayPause={handlePlayPause}
                onSpeedChange={handleSpeedChange}
              />
            </Box>
            <Box className={classes.playerSectionRight}>
              <SessionTabs
                activeTab={activeTab}
                sessionData={sessionData}
                currentTime={currentTime}
                scrollToTimestamp={scrollToTimestamp}
                onEventClick={handleSpanClick}
                networkViewMode={networkViewMode}
                onTabChange={setActiveTab}
                onCriticalInteractionClick={handleCriticalInteractionClick}
                onNetworkViewModeChange={setNetworkViewMode}
              />
            </Box>
          </Box>
        </>
      )}

      {/* Session Timeline section commented out
      <SessionTimelineSection
        flameChartData={flameChartData}
        sessionDuration={sessionDuration}
        sessionStartTime={sessionStartTime}
        totalDepth={totalDepth}
        onItemClick={handleSpanClick}
      />
      */}
      <DetailsSidebar item={selectedSpan} onClose={handleCloseSidebar} />
    </Box>
  );
};
