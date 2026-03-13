import { useParams, useNavigate } from "react-router-dom";
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

import { SessionHeader } from "./components/SessionHeader";
import { SessionSummary } from "./components/SessionSummary";
import { SessionTabs } from "./components/SessionTabs";
import { SessionPlayerSection } from "./components/SessionPlayerSection";
import { SessionTimelineSection } from "./components/SessionTimelineSection";
import { getSessionReplayImages } from "../../services/sessionReplay/sessionReplayImages";
import { DEFAULTS } from "./constants/strings";
import { useSessionDetail } from "./hooks/useSessionDetail";
import { useSessionReplaySnapshots } from "./hooks/useSessionReplaySnapshots";
import classes from "./SessionReplayDetail.module.css";
import { useState, useMemo, useEffect } from "react";

export const SessionReplayDetail: React.FC = () => {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<string>("all");
  const [selectedSpan, setSelectedSpan] = useState<FlameChartNode | null>(null);
  const [scrollToTimestamp, setScrollToTimestamp] = useState<{
    t0: number;
    t1: number;
  } | null>(null);

  const [eventsViewMode, setEventsViewMode] = useState<"text" | "graph">(
    "text",
  );
  const [consoleViewMode, setConsoleViewMode] = useState<"text" | "graph">(
    "text",
  );
  const [networkViewMode, setNetworkViewMode] = useState<"text" | "graph">(
    "text",
  );
  const [performanceViewMode, setPerformanceViewMode] = useState<
    "text" | "graph"
  >("text");

  const [activePersona, setActivePersona] = useState<PersonaType>("all");

  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);
  const useSnapshotApi =
    process.env.REACT_APP_USE_MOCK_SESSION_REPLAY !== "true";

  const {
    data: apiSessionData,
    isLoading: sessionLoading,
    isError: sessionError,
  } = useSessionDetail({
    sessionId: sessionId ?? undefined,
    includeEvents: true,
    enabled: !!sessionId,
  });

  const sessionData = useMemo(() => {
    if (apiSessionData) return apiSessionData;
    return getMockSessionDetail(sessionId || DEFAULTS.SESSION_ID_UNKNOWN);
  }, [apiSessionData, sessionId]);

  const snapshotSessionStart =
    sessionData.startTime ?? new Date().toISOString();

  const {
    images: snapshotImages,
    loading: snapshotLoading,
    error: snapshotError,
  } = useSessionReplaySnapshots({
    sessionId: sessionId ?? undefined,
    sessionStartTime: snapshotSessionStart,
    currentTime,
    enabled: useSnapshotApi && !!sessionId,
  });

  const [mockImages, setMockImages] = useState<any[]>([]);
  const [mockImagesLoading, setMockImagesLoading] = useState(false);

  useEffect(() => {
    if (!useSnapshotApi && sessionId) {
      const loadImages = async () => {
        setMockImagesLoading(true);
        try {
          const images = await getSessionReplayImages(
            sessionId,
            new Date(sessionData.startTime),
            10,
          );
          setMockImages(images);
        } catch (error) {
          console.error("Failed to load session replay images:", error);
        } finally {
          setMockImagesLoading(false);
        }
      };
      loadImages();
    }
  }, [useSnapshotApi, sessionId, sessionData.startTime]);

  const replayImages = useSnapshotApi ? snapshotImages : mockImages;
  const imagesLoading = useSnapshotApi ? snapshotLoading : mockImagesLoading;

  const { flameChartData, sessionDuration, sessionStartTime, totalDepth } =
    useMemo(() => {
      return transformToFlameChart(
        sessionData.traces,
        sessionData.logs,
        sessionData.exceptions,
      );
    }, [sessionData]);

  const handleBack = () => {
    navigate("/session-replay/sessions");
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

  const handlePlayPause = () => {
    setIsPlaying(!isPlaying);
  };

  const handleTimelineChange = (value: number) => {
    setCurrentTime(value);
  };

  const handleTimeUpdate = (time: number) => {
    if (time <= sessionData.duration) {
      setCurrentTime(time);
    } else {
      setIsPlaying(false);
      setCurrentTime(sessionData.duration);
    }
  };

  const handleSpeedChange = (speed: number) => {
    setPlaybackSpeed(speed);
  };

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
      <SessionHeader sessionData={sessionData} onBack={handleBack} />

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
                eventsViewMode={eventsViewMode}
                consoleViewMode={consoleViewMode}
                networkViewMode={networkViewMode}
                performanceViewMode={performanceViewMode}
                onTabChange={setActiveTab}
                onCriticalInteractionClick={handleCriticalInteractionClick}
                onEventsViewModeChange={setEventsViewMode}
                onConsoleViewModeChange={setConsoleViewMode}
                onNetworkViewModeChange={setNetworkViewMode}
                onPerformanceViewModeChange={setPerformanceViewMode}
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
