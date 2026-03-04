import { useParams, useNavigate } from "react-router-dom";
import {
  Box,
  Button,
  Paper,
  Group,
  Text,
  Badge,
  Stack,
  Grid,
  Tabs,
  Card,
  Timeline,
  Code,
  Table,
  Slider,
  ActionIcon,
  RingProgress,
  ScrollArea,
  Menu,
  UnstyledButton,
  Tooltip,
  SegmentedControl,
} from "@mantine/core";
import {
  IconArrowLeft,
  IconClock,
  IconDeviceMobile,
  IconUser,
  IconStar,
  IconAlertTriangle,
  IconActivity,
  IconBug,
  IconInfoCircle,
  IconList,
  IconTerminal,
  IconNetwork,
  IconGauge,
  IconCheck,
  IconX,
  IconMinus,
  IconChevronRight,
  IconPlayerPlay,
  IconPlayerPause,
  IconPlayerSkipForward,
  IconPlayerSkipBack,
  IconMaximize,
  IconArrowsMaximize,
  IconZoomIn,
  IconZoomOut,
  IconReload,
  IconHeadset,
  IconChartLine,
  IconCode,
  IconUsers,
  IconChevronDown,
} from "@tabler/icons-react";
import { FlameChart } from "../SessionTimeline/components/FlameChart";
import { DetailsSidebar } from "../SessionTimeline/components/DetailsSidebar";
import {
  FlameChartNode,
  transformToFlameChart,
} from "../SessionTimeline/utils/flameChartTransform";
import { getMockSessionDetail } from "../../services/sessionReplay/mockSessionDetail";
import { useSessionAnalysis } from "./hooks/useSessionAnalysis";
import { PersonaType } from "../../contexts/PersonaContext";
import { SupportSummaryTab } from "./components/SupportSummaryTab";
import { BusinessImpactTab } from "./components/BusinessImpactTab";
import { TechnicalTab } from "./components/TechnicalTab";
import { PerformanceVisualization } from "./components/PerformanceVisualization";
import { NetworkVisualization } from "./components/NetworkVisualization";
import { EventsVisualization } from "./components/EventsVisualization";
import { ConsoleVisualization } from "./components/ConsoleVisualization";
import { InfoVisualization } from "./components/InfoVisualization";
import { AllTab } from "./components/AllTab";
import { RawSessionEvents } from "./components/RawSessionEvents";
import { SessionReplayPlayer } from "./components/SessionReplayPlayer";
import { getSessionReplayImages } from "../../services/sessionReplay/sessionReplayImages";
import classes from "./SessionReplayDetail.module.css";
import { useState, useMemo, useEffect } from "react";
import dayjs from "dayjs";

/**
 * Session Replay Detail Page - PERSONA-AWARE
 *
 * NOW SUPPORTS 3 PERSONAS:
 * - Support: "What broke for the user?" → Plain language, quick actions
 * - Product: "What's the business impact?" → Conversion, revenue, patterns
 * - Tech: "What's the root cause?" → Code refs, errors, reproduce
 */

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
}

function formatTimestamp(ms: number, sessionStart: Date): string {
  const time = new Date(sessionStart.getTime() + ms);
  return dayjs(time).format("HH:mm:ss.SSS");
}

export const SessionReplayDetail: React.FC = () => {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<string>("all");
  const [selectedSpan, setSelectedSpan] = useState<FlameChartNode | null>(null);
  const [scrollToTimestamp, setScrollToTimestamp] = useState<{
    t0: number;
    t1: number;
  } | null>(null);

  // View mode for each tab (text vs graph)
  const [infoViewMode, setInfoViewMode] = useState<"text" | "graph">("text");
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

  // PERSONA STATE
  const [activePersona, setActivePersona] = useState<PersonaType>("all");

  // Player state
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);
  const [replayImages, setReplayImages] = useState<any[]>([]);
  const [imagesLoading, setImagesLoading] = useState(false);

  // Get mock data
  const sessionData = useMemo(
    () => getMockSessionDetail(sessionId || "session_unknown"),
    [sessionId],
  );

  // Load session replay images
  useEffect(() => {
    const loadImages = async () => {
      if (!sessionId) return;
      
      setImagesLoading(true);
      try {
        const images = await getSessionReplayImages(
          sessionId,
          new Date(sessionData.startTime),
          10 // 10 fps
        );
        setReplayImages(images);
      } catch (error) {
        console.error("Failed to load session replay images:", error);
      } finally {
        setImagesLoading(false);
      }
    };

    loadImages();
  }, [sessionId, sessionData.startTime]);

  // Load session replay images
  useEffect(() => {
    const loadImages = async () => {
      if (!sessionId) return;
      
      setImagesLoading(true);
      try {
        const images = await getSessionReplayImages(
          sessionId,
          new Date(sessionData.startTime),
          10 // 10 fps
        );
        setReplayImages(images);
      } catch (error) {
        console.error("Failed to load session replay images:", error);
      } finally {
        setImagesLoading(false);
      }
    };

    loadImages();
  }, [sessionId, sessionData.startTime]);

  // AUTO-DETECT ISSUES AND GENERATE PERSONA SUMMARIES
  const { sessionType, detectedIssues, personaSummaries } =
    useSessionAnalysis(sessionData);

  // Transform trace data to flame chart format
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

  const getQualityColor = (score: number) => {
    if (score >= 8) return "teal";
    if (score >= 6) return "yellow";
    return "red";
  };

  const getStatusIcon = (status: "success" | "failed" | "not_attempted") => {
    if (status === "success") return <IconCheck size={16} />;
    if (status === "failed") return <IconX size={16} />;
    return <IconMinus size={16} />;
  };

  const getStatusColor = (status: "success" | "failed" | "not_attempted") => {
    if (status === "success") return "teal";
    if (status === "failed") return "red";
    return "gray";
  };

  const handleSpanClick = (item: FlameChartNode) => {
    setSelectedSpan(item);
    // Future: Sync player to this timestamp
    setCurrentTime(item.start);
  };

  const handleCloseSidebar = () => {
    setSelectedSpan(null);
  };

  const handleCriticalInteractionClick = (t0: number, t1: number) => {
    console.log("Critical interaction clicked", { t0, t1 });

    // First, scroll the page to bring Raw Session Events into view
    const rawEventsSection = document.querySelector(
      "[data-raw-events-section]",
    );
    if (rawEventsSection) {
      rawEventsSection.scrollIntoView({ behavior: "smooth", block: "start" });
    }

    // Reset first to trigger useEffect even if clicking the same interaction again
    setScrollToTimestamp(null);
    // Then set the new timestamp after a delay to ensure Raw Session Events is visible
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

  const formatPlayerTime = (ms: number): string => {
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
  };

  const getPersonaIcon = (persona: PersonaType) => {
    switch (persona) {
      case "support":
        return <IconHeadset size={16} />;
      case "product":
        return <IconChartLine size={16} />;
      case "tech":
        return <IconCode size={16} />;
      default:
        return <IconUsers size={16} />;
    }
  };

  const getPersonaLabel = (persona: PersonaType) => {
    switch (persona) {
      case "support":
        return "Support";
      case "product":
        return "Product";
      case "tech":
        return "Technical";
      default:
        return "All Views";
    }
  };

  const getPersonaColor = (persona: PersonaType) => {
    switch (persona) {
      case "support":
        return "blue";
      case "product":
        return "violet";
      case "tech":
        return "orange";
      default:
        return "gray";
    }
  };

  return (
    <Box className={classes.container}>
      {/* Header */}
      <Paper className={classes.header} mb="md">
        <Group justify="space-between" align="center" wrap="nowrap">
          <Group gap="md">
            <Button
              variant="subtle"
              color="teal"
              leftSection={<IconArrowLeft size={16} />}
              onClick={handleBack}
            >
              Back
            </Button>
            <Text size="sm" ff="monospace" c="dimmed">
              {sessionData.sessionId}
            </Text>
          </Group>

          <Group gap="lg">
            {/* Quality Score - Circular Badge */}
            <Group gap="xs">
              <RingProgress
                size={50}
                thickness={6}
                sections={[
                  {
                    value: (sessionData.interactionQuality / 10) * 100,
                    color: getQualityColor(sessionData.interactionQuality),
                  },
                ]}
                label={
                  <Text size="sm" fw={700} ta="center">
                    {sessionData.interactionQuality.toFixed(1)}
                  </Text>
                }
              />
              <Box>
                <Text size="xs" fw={600}>
                  Quality Score
                </Text>
              </Box>
            </Group>

            {/* User Info */}
            <Group gap="xs">
              <IconUser size={16} />
              <Text size="sm" fw={500}>
                {sessionData.userId}
              </Text>
              <Badge size="xs" color="blue" variant="light">
                IDENTIFIED
              </Badge>
            </Group>

            {/* Session Time */}
            <Group gap="xs">
              <IconClock size={16} />
              <Text size="sm" c="dimmed">
                {new Date(sessionData.startTime).toLocaleString("en-US", {
                  month: "short",
                  day: "numeric",
                  hour: "2-digit",
                  minute: "2-digit",
                })}{" "}
                Session Time
              </Text>
            </Group>

            {/* Device Info */}
            <Group gap="xs">
              <IconDeviceMobile size={16} />
              <Text size="sm">
                {sessionData.device} {sessionData.os}
              </Text>
            </Group>

            {/* Duration */}
            <Text size="sm" fw={500}>
              {formatDuration(sessionData.duration)} Duration
            </Text>
          </Group>
        </Group>
      </Paper>

      {/* Main Content - Player First Layout */}
      <Stack gap="lg" mb="lg">
        {/* Tabs Section - Above Player */}
        {activePersona === "all" && (
          <Paper className={classes.allTabContainer}>
            <Tabs
              value={activeTab}
              onChange={(value) => setActiveTab(value || "all")}
            >
              <Tabs.List>
                <Tabs.Tab value="all">All</Tabs.Tab>
                <Tabs.Tab value="events" leftSection={<IconList size={14} />}>
                  Events
                </Tabs.Tab>
                <Tabs.Tab
                  value="console"
                  leftSection={<IconTerminal size={14} />}
                >
                  Console
                </Tabs.Tab>
                <Tabs.Tab
                  value="network"
                  leftSection={<IconNetwork size={14} />}
                >
                  Network
                </Tabs.Tab>
                <Tabs.Tab
                  value="performance"
                  leftSection={<IconGauge size={14} />}
                >
                  Performance
                </Tabs.Tab>
              </Tabs.List>

              <Box className={classes.tabContent}>
                <Tabs.Panel value="all">
                  <AllTab
                    sessionData={sessionData}
                    onCriticalInteractionClick={handleCriticalInteractionClick}
                  />
                </Tabs.Panel>

                <Tabs.Panel value="events">
                  <Stack gap="xs">
                    <Group justify="flex-end">
                      <SegmentedControl
                        size="xs"
                        value={eventsViewMode}
                        onChange={(value) =>
                          setEventsViewMode(value as "text" | "graph")
                        }
                        data={[
                          { label: "Text", value: "text" },
                          { label: "Graph", value: "graph" },
                        ]}
                      />
                    </Group>
                    {eventsViewMode === "graph" ? (
                      <EventsVisualization
                        events={sessionData.events}
                        sessionStartTime={new Date(sessionData.startTime)}
                      />
                    ) : (
                      <Timeline
                        active={sessionData.events.length}
                        bulletSize={20}
                        lineWidth={2}
                      >
                        {sessionData.events.map((event, idx) => (
                          <Timeline.Item
                            key={idx}
                            bullet={
                              <Box
                                style={{
                                  width: 8,
                                  height: 8,
                                  borderRadius: "50%",
                                  background: "var(--mantine-color-teal-6)",
                                }}
                              />
                            }
                          >
                            <Text size="xs" c="dimmed">
                              {formatTimestamp(
                                event.timestamp,
                                new Date(sessionData.startTime),
                              )}
                            </Text>
                            <Text size="sm" fw={500}>
                              {event.description}
                            </Text>
                            <Badge size="xs" variant="light" mt={4}>
                              {event.type}
                            </Badge>
                          </Timeline.Item>
                        ))}
                      </Timeline>
                    )}
                  </Stack>
                </Tabs.Panel>

                <Tabs.Panel value="console">
                  <Stack gap="xs">
                    <Group justify="flex-end">
                      <SegmentedControl
                        size="xs"
                        value={consoleViewMode}
                        onChange={(value) =>
                          setConsoleViewMode(value as "text" | "graph")
                        }
                        data={[
                          { label: "Text", value: "text" },
                          { label: "Graph", value: "graph" },
                        ]}
                      />
                    </Group>
                    {consoleViewMode === "graph" ? (
                      <ConsoleVisualization
                        consoleLogs={sessionData.consoleLogs}
                        sessionStartTime={new Date(sessionData.startTime)}
                      />
                    ) : (
                      <>
                        {sessionData.consoleLogs.map((log, idx) => (
                          <Card key={idx} padding="xs" withBorder>
                            <Group justify="space-between" mb={4}>
                              <Badge
                                size="xs"
                                color={
                                  log.level === "error"
                                    ? "red"
                                    : log.level === "warn"
                                      ? "yellow"
                                      : "gray"
                                }
                              >
                                {log.level.toUpperCase()}
                              </Badge>
                              <Text size="xs" c="dimmed">
                                {formatTimestamp(
                                  log.timestamp,
                                  new Date(sessionData.startTime),
                                )}
                              </Text>
                            </Group>
                            <Code block style={{ fontSize: 11 }}>
                              {log.message}
                            </Code>
                            {log.stackTrace && (
                              <Code
                                block
                                mt={4}
                                style={{ fontSize: 10 }}
                                c="red"
                              >
                                {log.stackTrace}
                              </Code>
                            )}
                          </Card>
                        ))}
                      </>
                    )}
                  </Stack>
                </Tabs.Panel>

                <Tabs.Panel value="network">
                  <Stack gap="xs">
                    <Group justify="flex-end">
                      <SegmentedControl
                        size="xs"
                        value={networkViewMode}
                        onChange={(value) =>
                          setNetworkViewMode(value as "text" | "graph")
                        }
                        data={[
                          { label: "Text", value: "text" },
                          { label: "Graph", value: "graph" },
                        ]}
                      />
                    </Group>
                    {networkViewMode === "graph" ? (
                      <NetworkVisualization
                        networkRequests={sessionData.networkRequests}
                        sessionStartTime={new Date(sessionData.startTime)}
                      />
                    ) : (
                      <>
                        {sessionData.networkRequests.map((req, idx) => (
                          <Card key={idx} padding="sm" withBorder>
                            <Group justify="space-between" mb={4}>
                              <Group gap="xs">
                                <Badge size="xs" variant="light">
                                  {req.method}
                                </Badge>
                                <Badge
                                  size="xs"
                                  color={
                                    req.status >= 200 && req.status < 300
                                      ? "teal"
                                      : "red"
                                  }
                                >
                                  {req.status}
                                </Badge>
                              </Group>
                              <Text size="xs" c="dimmed">
                                {req.duration}ms
                              </Text>
                            </Group>
                            <Text size="sm" ff="monospace" truncate="end">
                              {req.url}
                            </Text>
                            <Text size="xs" c="dimmed" mt={4}>
                              {formatTimestamp(
                                req.timestamp,
                                new Date(sessionData.startTime),
                              )}
                            </Text>
                          </Card>
                        ))}
                      </>
                    )}
                  </Stack>
                </Tabs.Panel>

                <Tabs.Panel value="performance">
                  <Stack gap="md">
                    <Group justify="flex-end">
                      <SegmentedControl
                        size="xs"
                        value={performanceViewMode}
                        onChange={(value) =>
                          setPerformanceViewMode(value as "text" | "graph")
                        }
                        data={[
                          { label: "Text", value: "text" },
                          { label: "Graph", value: "graph" },
                        ]}
                      />
                    </Group>
                    {performanceViewMode === "graph" ? (
                      <PerformanceVisualization
                        performance={sessionData.performance}
                      />
                    ) : (
                      <>
                        <Box>
                          <Text
                            size="xs"
                            tt="uppercase"
                            fw={600}
                            c="dimmed"
                            mb="xs"
                          >
                            Interaction Metrics
                          </Text>
                          <Table>
                            <Table.Thead>
                              <Table.Tr>
                                <Table.Th>Interaction</Table.Th>
                                <Table.Th>Duration</Table.Th>
                                <Table.Th>Apdex</Table.Th>
                              </Table.Tr>
                            </Table.Thead>
                            <Table.Tbody>
                              {sessionData.performance.interactionMetrics.map(
                                (metric) => (
                                  <Table.Tr key={metric.interactionId}>
                                    <Table.Td>
                                      <Text size="sm">
                                        {metric.interactionName}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td>
                                      <Text size="sm">{metric.duration}ms</Text>
                                    </Table.Td>
                                    <Table.Td>
                                      <Badge
                                        size="sm"
                                        color={
                                          metric.apdexScore >= 0.8
                                            ? "teal"
                                            : metric.apdexScore >= 0.5
                                              ? "yellow"
                                              : "red"
                                        }
                                      >
                                        {metric.apdexScore.toFixed(2)}
                                      </Badge>
                                    </Table.Td>
                                  </Table.Tr>
                                ),
                              )}
                            </Table.Tbody>
                          </Table>
                        </Box>
                      </>
                    )}
                  </Stack>
                </Tabs.Panel>
              </Box>
            </Tabs>
          </Paper>
        )}

        {/* Session Replay Player - Full Width */}
        <Paper className={classes.playerContainer}>
          {/* Player Header */}
          <Group justify="space-between" className={classes.playerHeader}>
            <Group gap="xs">
              <Text size="sm" fw={500}>
                {sessionData.platform} {sessionData.device} · {sessionData.os}
              </Text>
            </Group>
            <Group gap="xs">
              <ActionIcon variant="subtle" size="sm" color="gray">
                <IconZoomIn size={16} />
              </ActionIcon>
              <ActionIcon variant="subtle" size="sm" color="gray">
                <IconZoomOut size={16} />
              </ActionIcon>
              <ActionIcon variant="subtle" size="sm" color="gray">
                <IconMaximize size={16} />
              </ActionIcon>
            </Group>
          </Group>

          {/* Player Viewport */}
          <Box className={classes.playerViewport}>
            {imagesLoading ? (
              <Stack align="center" gap="md">
                <IconReload size={32} className={classes.loadingSpinner} />
                <Text size="sm" c="dimmed">Loading session replay images...</Text>
              </Stack>
            ) : replayImages.length > 0 ? (
              <>
                <SessionReplayPlayer
                  images={replayImages}
                  currentTime={currentTime}
                  isPlaying={isPlaying}
                  playbackSpeed={playbackSpeed}
                  sessionData={sessionData}
                  onTimeUpdate={handleTimeUpdate}
                />
                {/* Sync Marker Overlay */}
                {selectedSpan && (
                  <Box className={classes.syncMarker}>
                    <Badge size="sm" color="teal" variant="filled">
                      Synced to: {formatPlayerTime(selectedSpan.start)}
                    </Badge>
                  </Box>
                )}
              </>
            ) : (
              <Box className={classes.playerPlaceholder}>
                <Stack align="center" gap="md">
                  <IconDeviceMobile
                    size={64}
                    stroke={1.5}
                    color="var(--mantine-color-gray-5)"
                  />
                  <Text size="lg" fw={600} c="dimmed">
                    Session Replay Player
                  </Text>
                  <Text size="sm" c="dimmed" ta="center" maw={400}>
                    This area will display the session recording.
                    <br />
                    For web: rrweb DOM replay | For mobile: Wireframe
                    reconstruction
                  </Text>
                  <Badge
                    size="lg"
                    variant="light"
                    color="blue"
                    leftSection={<IconReload size={14} />}
                  >
                    Integration Ready
                  </Badge>
                </Stack>
              </Box>
            )}
          </Box>

          {/* Player Controls */}
          <Box className={classes.playerControls}>
            {/* Timeline Scrubber */}
            <Box mb="xs">
              <Slider
                value={currentTime}
                onChange={handleTimelineChange}
                min={0}
                max={sessionData.duration}
                size="sm"
                color="teal"
                label={(value) => formatPlayerTime(value)}
                marks={sessionData.criticalInteractions
                  .filter((i) => i.timestamp)
                  .map((i) => ({
                    value: i.timestamp!,
                    label: "",
                  }))}
                styles={{
                  mark: {
                    backgroundColor: "var(--mantine-color-red-5)",
                    borderColor: "var(--mantine-color-red-5)",
                    width: 6,
                    height: 6,
                  },
                }}
              />
              <Group justify="space-between" mt={4}>
                <Text size="xs" c="dimmed">
                  {formatPlayerTime(currentTime)}
                </Text>
                <Text size="xs" c="dimmed">
                  {formatPlayerTime(sessionData.duration)}
                </Text>
              </Group>
            </Box>

            {/* Control Buttons */}
            <Group justify="space-between" align="center">
              <Group gap="xs">
                <ActionIcon
                  size="lg"
                  variant="filled"
                  color="teal"
                  onClick={handlePlayPause}
                >
                  {isPlaying ? (
                    <IconPlayerPause size={18} />
                  ) : (
                    <IconPlayerPlay size={18} />
                  )}
                </ActionIcon>
                <ActionIcon size="md" variant="subtle" color="gray">
                  <IconPlayerSkipBack size={16} />
                </ActionIcon>
                <ActionIcon size="md" variant="subtle" color="gray">
                  <IconPlayerSkipForward size={16} />
                </ActionIcon>
              </Group>

              <Group gap="md">
                <Group gap={4}>
                  <Text size="xs" c="dimmed">
                    Speed:
                  </Text>
                  <Group gap={4}>
                    {[0.5, 1, 1.5, 2].map((speed) => (
                      <Button
                        key={speed}
                        size="xs"
                        variant={playbackSpeed === speed ? "filled" : "subtle"}
                        color="gray"
                        onClick={() => handleSpeedChange(speed)}
                      >
                        {speed}x
                      </Button>
                    ))}
                  </Group>
                </Group>

                <ActionIcon size="md" variant="subtle" color="gray">
                  <IconArrowsMaximize size={16} />
                </ActionIcon>
              </Group>
            </Group>
          </Box>
        </Paper>

        {/* Raw Session Events */}
        <Paper
          className={classes.rawEventsContainer}
          mt="md"
          data-raw-events-section
        >
          <RawSessionEvents
            sessionData={sessionData}
            scrollToTimestamp={scrollToTimestamp}
            onEventClick={handleSpanClick}
          />
        </Paper>
      </Stack>

      {/* Flame Chart Timeline */}
      <Paper className={classes.timelineSection}>
        <Group justify="space-between" mb="md">
          <Text size="md" fw={600}>
            Session Timeline
          </Text>
          <Badge
            size="sm"
            variant="light"
            color="teal"
            leftSection={<IconBug size={12} />}
          >
            {totalDepth} levels deep
          </Badge>
        </Group>

        <FlameChart
          data={flameChartData}
          sessionDuration={sessionDuration}
          sessionStartTime={sessionStartTime}
          totalDepth={totalDepth}
          onItemClick={handleSpanClick}
          isLoading={false}
        />
      </Paper>

      {/* Details Sidebar */}
      <DetailsSidebar item={selectedSpan} onClose={handleCloseSidebar} />
    </Box>
  );
};
