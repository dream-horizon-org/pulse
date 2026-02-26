import { useParams, useNavigate } from 'react-router-dom';
import { Box, Button, Paper, Group, Text, Badge, Stack, Grid, Tabs, Card, Timeline, Code, Table, Slider, ActionIcon, RingProgress, ScrollArea, Menu, UnstyledButton, Tooltip, SegmentedControl } from '@mantine/core';
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
  IconChevronDown
} from '@tabler/icons-react';
import { FlameChart } from '../SessionTimeline/components/FlameChart';
import { DetailsSidebar } from '../SessionTimeline/components/DetailsSidebar';
import { FlameChartNode, transformToFlameChart } from '../SessionTimeline/utils/flameChartTransform';
import { getMockSessionDetail } from '../../services/sessionReplay/mockSessionDetail';
import { useSessionAnalysis } from './hooks/useSessionAnalysis';
import { PersonaType } from '../../contexts/PersonaContext';
import { SupportSummaryTab } from './components/SupportSummaryTab';
import { BusinessImpactTab } from './components/BusinessImpactTab';
import { TechnicalTab } from './components/TechnicalTab';
import { PerformanceVisualization } from './components/PerformanceVisualization';
import { NetworkVisualization } from './components/NetworkVisualization';
import { EventsVisualization } from './components/EventsVisualization';
import { ConsoleVisualization } from './components/ConsoleVisualization';
import { InfoVisualization } from './components/InfoVisualization';
import classes from './SessionReplayDetail.module.css';
import { useState, useMemo } from 'react';
import dayjs from 'dayjs';

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
  const [activeTab, setActiveTab] = useState<string>('session-info');
  const [selectedSpan, setSelectedSpan] = useState<FlameChartNode | null>(null);
  
  // View mode for each tab (text vs graph)
  const [infoViewMode, setInfoViewMode] = useState<'text' | 'graph'>('text');
  const [eventsViewMode, setEventsViewMode] = useState<'text' | 'graph'>('text');
  const [consoleViewMode, setConsoleViewMode] = useState<'text' | 'graph'>('text');
  const [networkViewMode, setNetworkViewMode] = useState<'text' | 'graph'>('text');
  const [performanceViewMode, setPerformanceViewMode] = useState<'text' | 'graph'>('text');
  
  // PERSONA STATE
  const [activePersona, setActivePersona] = useState<PersonaType>('all');
  
  // Player state
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);

  // Get mock data
  const sessionData = useMemo(() => 
    getMockSessionDetail(sessionId || 'session_unknown'), 
    [sessionId]
  );

  // AUTO-DETECT ISSUES AND GENERATE PERSONA SUMMARIES
  const { sessionType, detectedIssues, personaSummaries } = useSessionAnalysis(sessionData);

  // Transform trace data to flame chart format
  const {
    flameChartData,
    sessionDuration,
    sessionStartTime,
    totalDepth,
  } = useMemo(() => {
    return transformToFlameChart(
      sessionData.traces,
      sessionData.logs,
      sessionData.exceptions
    );
  }, [sessionData]);

  const handleBack = () => {
    navigate('/session-replay/sessions');
  };

  const getQualityColor = (score: number) => {
    if (score >= 8) return 'teal';
    if (score >= 6) return 'yellow';
    return 'red';
  };

  const getStatusIcon = (status: "success" | "failed" | "not_attempted") => {
    if (status === 'success') return <IconCheck size={16} />;
    if (status === 'failed') return <IconX size={16} />;
    return <IconMinus size={16} />;
  };

  const getStatusColor = (status: "success" | "failed" | "not_attempted") => {
    if (status === 'success') return 'teal';
    if (status === 'failed') return 'red';
    return 'gray';
  };

  const handleSpanClick = (item: FlameChartNode) => {
    setSelectedSpan(item);
    // Future: Sync player to this timestamp
    setCurrentTime(item.start);
  };

  const handleCloseSidebar = () => {
    setSelectedSpan(null);
  };

  const handlePlayPause = () => {
    setIsPlaying(!isPlaying);
  };

  const handleTimelineChange = (value: number) => {
    setCurrentTime(value);
    // Future: Seek player to this time
  };

  const handleSpeedChange = (speed: number) => {
    setPlaybackSpeed(speed);
  };

  const formatPlayerTime = (ms: number): string => {
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  const getPersonaIcon = (persona: PersonaType) => {
    switch (persona) {
      case 'support': return <IconHeadset size={16} />;
      case 'product': return <IconChartLine size={16} />;
      case 'tech': return <IconCode size={16} />;
      default: return <IconUsers size={16} />;
    }
  };

  const getPersonaLabel = (persona: PersonaType) => {
    switch (persona) {
      case 'support': return 'Support';
      case 'product': return 'Product';
      case 'tech': return 'Technical';
      default: return 'All Views';
    }
  };

  const getPersonaColor = (persona: PersonaType) => {
    switch (persona) {
      case 'support': return 'blue';
      case 'product': return 'violet';
      case 'tech': return 'orange';
      default: return 'gray';
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

            <Box>
              <Group gap="xs" align="center">
                <Text size="sm" fw={500}>Session:</Text>
                <Text size="sm" ff="monospace" c="dimmed">{sessionData.sessionId}</Text>
                <Text size="sm" c="dimmed">•</Text>
                <Group gap={4}>
                  <IconUser size={14} />
                  <Text size="sm">{sessionData.userId}</Text>
                </Group>
                <Text size="sm" c="dimmed">•</Text>
                <Group gap={4}>
                  <IconClock size={14} />
                  <Text size="sm">{new Date(sessionData.startTime).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</Text>
                </Group>
              </Group>
            </Box>
          </Group>

          <Group gap="md">
            <Badge 
              size="lg" 
              color={getQualityColor(sessionData.interactionQuality)}
              leftSection={<IconStar size={14} />}
              variant="light"
            >
              Quality: {sessionData.interactionQuality}/10
            </Badge>
            <Badge size="lg" color="blue" variant="light" leftSection={<IconDeviceMobile size={14} />}>
              {sessionData.platform}
            </Badge>
            
            {/* Persona Selector - Clean dropdown */}
            <Menu shadow="md" width={180}>
              <Menu.Target>
                <Tooltip label="Switch view for different roles">
                  <UnstyledButton className={classes.personaSelector}>
                    <Group gap="xs">
                      {getPersonaIcon(activePersona)}
                      <Text size="sm" fw={500}>{getPersonaLabel(activePersona)}</Text>
                      <IconChevronDown size={14} />
                    </Group>
                  </UnstyledButton>
                </Tooltip>
              </Menu.Target>

              <Menu.Dropdown>
                <Menu.Label>View as</Menu.Label>
                <Menu.Item
                  leftSection={<IconUsers size={16} />}
                  onClick={() => setActivePersona('all')}
                  color={activePersona === 'all' ? 'teal' : undefined}
                  fw={activePersona === 'all' ? 600 : undefined}
                >
                  All Views
                </Menu.Item>
                <Menu.Divider />
                <Menu.Item
                  leftSection={<IconHeadset size={16} />}
                  onClick={() => setActivePersona('support')}
                  color={activePersona === 'support' ? getPersonaColor('support') : undefined}
                  fw={activePersona === 'support' ? 600 : undefined}
                >
                  Support
                </Menu.Item>
                <Menu.Item
                  leftSection={<IconChartLine size={16} />}
                  onClick={() => setActivePersona('product')}
                  color={activePersona === 'product' ? getPersonaColor('product') : undefined}
                  fw={activePersona === 'product' ? 600 : undefined}
                >
                  Product
                </Menu.Item>
                <Menu.Item
                  leftSection={<IconCode size={16} />}
                  onClick={() => setActivePersona('tech')}
                  color={activePersona === 'tech' ? getPersonaColor('tech') : undefined}
                  fw={activePersona === 'tech' ? 600 : undefined}
                >
                  Technical
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </Paper>

      {/* Summary Stats */}
      <Paper className={classes.summaryStats} mb="lg">
        <Group gap="xl">
          <Box className={classes.statCard}>
            <Group gap="xs">
              <IconActivity size={16} color="var(--mantine-color-teal-6)" />
              <Text className={classes.statValue}>{sessionData.traces.rows.length}</Text>
            </Group>
            <Text className={classes.statLabel}>Spans</Text>
          </Box>
          
          <Box className={classes.statCard}>
            <Group gap="xs">
              <IconList size={16} color="var(--mantine-color-blue-6)" />
              <Text className={classes.statValue}>{sessionData.logs.rows.length}</Text>
            </Group>
            <Text className={classes.statLabel}>Logs</Text>
          </Box>
          
          {sessionData.exceptions.rows.length > 0 && (
            <Box className={classes.statCardError}>
              <Group gap="xs">
                <IconAlertTriangle size={16} color="var(--mantine-color-red-6)" />
                <Text className={classes.statValue}>{sessionData.exceptions.rows.length}</Text>
              </Group>
              <Text className={classes.statLabel}>Exceptions</Text>
            </Box>
          )}
          
          <Box className={classes.statCard}>
            <Text className={classes.statValue}>{formatDuration(sessionData.duration)}</Text>
            <Text className={classes.statLabel}>Duration</Text>
          </Box>
        </Group>
      </Paper>

      {/* Main Content - Split View */}
      <Grid gutter="lg" mb="lg">
        {/* Left: Replay Player (60%) */}
        <Grid.Col span={{ base: 12, md: 7 }}>
          <Paper className={classes.playerContainer}>
            {/* Player Header */}
            <Group justify="space-between" className={classes.playerHeader} mb="sm">
              <Group gap="xs">
                <Badge size="sm" variant="light" color="blue">
                  {sessionData.platform}
                </Badge>
                <Text size="xs" c="dimmed">
                  {sessionData.device} • {sessionData.os}
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
              <Box className={classes.playerPlaceholder}>
                <Stack align="center" gap="md">
                  <IconDeviceMobile size={64} stroke={1.5} color="var(--mantine-color-gray-5)" />
                  <Text size="lg" fw={600} c="dimmed">Session Replay Player</Text>
                  <Text size="sm" c="dimmed" ta="center" maw={400}>
                    This area will display the session recording.<br />
                    For web: rrweb DOM replay | For mobile: Wireframe reconstruction
                  </Text>
                  <Badge size="lg" variant="light" color="blue" leftSection={<IconReload size={14} />}>
                    Integration Ready
                  </Badge>
                </Stack>
              </Box>

              {/* Sync Marker Overlay */}
              {selectedSpan && (
                <Box className={classes.syncMarker}>
                  <Badge size="sm" color="teal" variant="filled">
                    Synced to: {formatPlayerTime(selectedSpan.start)}
                  </Badge>
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
                    .filter(i => i.timestamp)
                    .map(i => ({
                      value: i.timestamp!,
                      label: ''
                    }))}
                  styles={{
                    mark: {
                      backgroundColor: 'var(--mantine-color-red-5)',
                      borderColor: 'var(--mantine-color-red-5)',
                      width: 6,
                      height: 6,
                    }
                  }}
                />
                <Group justify="space-between" mt={4}>
                  <Text size="xs" c="dimmed">{formatPlayerTime(currentTime)}</Text>
                  <Text size="xs" c="dimmed">{formatPlayerTime(sessionData.duration)}</Text>
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
                    {isPlaying ? <IconPlayerPause size={18} /> : <IconPlayerPlay size={18} />}
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
                    <Text size="xs" c="dimmed">Speed:</Text>
                    <Group gap={4}>
                      {[0.5, 1, 1.5, 2].map(speed => (
                        <Button
                          key={speed}
                          size="xs"
                          variant={playbackSpeed === speed ? 'filled' : 'subtle'}
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
        </Grid.Col>

        {/* Right: Context Tabs (40%) - PERSONA-AWARE! */}
        <Grid.Col span={{ base: 12, md: 5 }}>
          <Paper className={classes.contextPanel}>
            {/* PERSONA-SPECIFIC TABS */}
            {activePersona === 'support' ? (
              /* SUPPORT VIEW - No tabs, just the summary */
              <Box className={classes.tabContent}>
                <SupportSummaryTab sessionData={sessionData} detectedIssues={detectedIssues} />
              </Box>
            ) : activePersona === 'product' ? (
              /* PRODUCT VIEW - No tabs, just business metrics */
              <Box className={classes.tabContent}>
                <BusinessImpactTab sessionData={sessionData} />
              </Box>
            ) : activePersona === 'tech' ? (
              /* TECH VIEW - No tabs, just root cause */
              <Box className={classes.tabContent}>
                <TechnicalTab sessionData={sessionData} detectedIssues={detectedIssues} />
              </Box>
            ) : (
              /* ALL DATA VIEW - Keep original tabs */
              <Tabs value={activeTab} onChange={(value) => setActiveTab(value || 'session-info')}>
                <Tabs.List>
                  <Tabs.Tab value="session-info" leftSection={<IconInfoCircle size={14} />}>
                    Info
                  </Tabs.Tab>
                  <Tabs.Tab value="events" leftSection={<IconList size={14} />}>
                    Events
                  </Tabs.Tab>
                  <Tabs.Tab value="console" leftSection={<IconTerminal size={14} />}>
                    Console
                  </Tabs.Tab>
                  <Tabs.Tab value="network" leftSection={<IconNetwork size={14} />}>
                    Network
                  </Tabs.Tab>
                  <Tabs.Tab value="performance" leftSection={<IconGauge size={14} />}>
                    Performance
                  </Tabs.Tab>
                </Tabs.List>

                <Box className={classes.tabContent}>
                <Tabs.Panel value="session-info">
                  <Stack gap="lg">
                    <Group justify="flex-end">
                      <SegmentedControl
                        size="xs"
                        value={infoViewMode}
                        onChange={(value) => setInfoViewMode(value as 'text' | 'graph')}
                        data={[
                          { label: 'Text', value: 'text' },
                          { label: 'Graph', value: 'graph' },
                        ]}
                      />
                    </Group>
                    {infoViewMode === 'graph' ? (
                      <InfoVisualization
                        criticalInteractions={sessionData.criticalInteractions}
                        journey={sessionData.journey}
                      />
                    ) : (
                      <>
                    {/* Quality Score Card */}
                    <Card padding="md" withBorder>
                      <Group justify="space-between" align="center">
                        <Box>
                          <Text size="sm" fw={600} mb={4}>Interaction Quality</Text>
                          <Text size="xs" c="dimmed">Overall session health</Text>
                        </Box>
                        <RingProgress
                          size={80}
                          thickness={8}
                          sections={[
                            { value: (sessionData.interactionQuality / 10) * 100, color: getQualityColor(sessionData.interactionQuality) }
                          ]}
                          label={
                            <Text size="lg" fw={700} ta="center">
                              {sessionData.interactionQuality.toFixed(1)}
                            </Text>
                          }
                        />
                      </Group>
                    </Card>

                    {/* Device & Session Details */}
                    <Box>
                      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="xs">Device & Session Info</Text>
                      <Card padding="sm" withBorder>
                        <Stack gap="xs">
                          <Group justify="space-between">
                            <Text size="sm" c="dimmed">Device</Text>
                            <Text size="sm" fw={500}>{sessionData.device}</Text>
                          </Group>
                          <Group justify="space-between">
                            <Text size="sm" c="dimmed">OS</Text>
                            <Text size="sm" fw={500}>{sessionData.os}</Text>
                          </Group>
                          <Group justify="space-between">
                            <Text size="sm" c="dimmed">App Version</Text>
                            <Text size="sm" fw={500}>{sessionData.appVersion}</Text>
                          </Group>
                          {sessionData.geography && (
                            <Group justify="space-between">
                              <Text size="sm" c="dimmed">Location</Text>
                              <Text size="sm" fw={500}>{sessionData.geography.city}, {sessionData.geography.country}</Text>
                            </Group>
                          )}
                          <Group justify="space-between">
                            <Text size="sm" c="dimmed">User Type</Text>
                            <Badge size="sm" variant="light" color={sessionData.isAnonymous ? 'gray' : 'blue'}>
                              {sessionData.isAnonymous ? 'Anonymous' : 'Identified'}
                            </Badge>
                          </Group>
                        </Stack>
                      </Card>
                    </Box>

                    {/* User Journey */}
                    <Box>
                      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="xs">User Journey</Text>
                      <Card padding="sm" withBorder>
                        <ScrollArea>
                          <Group gap="xs" wrap="nowrap">
                            {sessionData.journey.map((path, idx) => (
                              <Group key={idx} gap={4} wrap="nowrap">
                                <Badge 
                                  variant={idx === sessionData.journey.length - 1 ? 'filled' : 'light'} 
                                  size="sm"
                                  color={path === '/error' ? 'red' : 'blue'}
                                >
                                  {path}
                                </Badge>
                                {idx < sessionData.journey.length - 1 && <IconChevronRight size={12} />}
                              </Group>
                            ))}
                          </Group>
                        </ScrollArea>
                      </Card>
                    </Box>

                    {/* Critical Interactions */}
                    <Box>
                      <Group justify="space-between" mb="xs">
                        <Text size="xs" tt="uppercase" fw={600} c="dimmed">Critical Interactions</Text>
                        <Badge size="xs" variant="light">
                          {sessionData.criticalInteractions.filter(i => i.status === 'success').length}/{sessionData.criticalInteractions.length} Successful
                        </Badge>
                      </Group>
                      <Card padding="sm" withBorder>
                        <Stack gap="sm">
                          {sessionData.criticalInteractions.map((interaction) => (
                            <Box key={interaction.interactionId}>
                              <Group justify="space-between" mb={4}>
                                <Group gap="xs">
                                  {getStatusIcon(interaction.status)}
                                  <Text size="sm" fw={500}>{interaction.displayName}</Text>
                                </Group>
                                <Badge size="sm" color={getStatusColor(interaction.status)} variant="light">
                                  {interaction.status === 'success' ? 'Success' : interaction.status === 'failed' ? 'Failed' : 'Not Attempted'}
                                </Badge>
                              </Group>
                              {interaction.timestamp !== undefined && (
                                <Group gap="md" pl={22}>
                                  <Text size="xs" c="dimmed">
                                    <IconClock size={12} style={{ verticalAlign: 'middle', marginRight: 2 }} />
                                    {formatTimestamp(interaction.timestamp, new Date(sessionData.startTime))}
                                  </Text>
                                  {interaction.latency !== undefined && (
                                    <Text size="xs" c={interaction.latency > 1000 ? 'red' : 'dimmed'}>
                                      Latency: {interaction.latency}ms
                                    </Text>
                                  )}
                                  {interaction.apdexScore !== undefined && (
                                    <Badge 
                                      size="xs" 
                                      variant="dot"
                                      color={interaction.apdexScore >= 0.8 ? 'teal' : interaction.apdexScore >= 0.5 ? 'yellow' : 'red'}
                                    >
                                      Apdex: {interaction.apdexScore.toFixed(2)}
                                    </Badge>
                                  )}
                                </Group>
                              )}
                            </Box>
                          ))}
                        </Stack>
                      </Card>
                    </Box>
                    </>
                    )}
                  </Stack>
                </Tabs.Panel>

                <Tabs.Panel value="events">
                  <Stack gap="xs">
                    <Group justify="flex-end">
                      <SegmentedControl
                        size="xs"
                        value={eventsViewMode}
                        onChange={(value) => setEventsViewMode(value as 'text' | 'graph')}
                        data={[
                          { label: 'Text', value: 'text' },
                          { label: 'Graph', value: 'graph' },
                        ]}
                      />
                    </Group>
                    {eventsViewMode === 'graph' ? (
                      <EventsVisualization
                        events={sessionData.events}
                        sessionStartTime={new Date(sessionData.startTime)}
                      />
                    ) : (
                      <>
                    <Timeline active={sessionData.events.length} bulletSize={20} lineWidth={2}>
                      {sessionData.events.map((event, idx) => (
                        <Timeline.Item key={idx} bullet={<Box style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--mantine-color-teal-6)' }} />}>
                          <Text size="xs" c="dimmed">{formatTimestamp(event.timestamp, new Date(sessionData.startTime))}</Text>
                          <Text size="sm" fw={500}>{event.description}</Text>
                          <Badge size="xs" variant="light" mt={4}>{event.type}</Badge>
                        </Timeline.Item>
                      ))}
                    </Timeline>
                    </>
                    )}
                  </Stack>
                </Tabs.Panel>

                <Tabs.Panel value="console">
                  <Stack gap="xs">
                    <Group justify="flex-end">
                      <SegmentedControl
                        size="xs"
                        value={consoleViewMode}
                        onChange={(value) => setConsoleViewMode(value as 'text' | 'graph')}
                        data={[
                          { label: 'Text', value: 'text' },
                          { label: 'Graph', value: 'graph' },
                        ]}
                      />
                    </Group>
                    {consoleViewMode === 'graph' ? (
                      <ConsoleVisualization
                        consoleLogs={sessionData.consoleLogs}
                        sessionStartTime={new Date(sessionData.startTime)}
                      />
                    ) : (
                      <>
                    {sessionData.consoleLogs.map((log, idx) => (
                      <Card key={idx} padding="xs" withBorder>
                        <Group justify="space-between" mb={4}>
                          <Badge size="xs" color={log.level === 'error' ? 'red' : log.level === 'warn' ? 'yellow' : 'gray'}>
                            {log.level.toUpperCase()}
                          </Badge>
                          <Text size="xs" c="dimmed">{formatTimestamp(log.timestamp, new Date(sessionData.startTime))}</Text>
                        </Group>
                        <Code block style={{ fontSize: 11 }}>{log.message}</Code>
                        {log.stackTrace && (
                          <Code block mt={4} style={{ fontSize: 10 }} c="red">{log.stackTrace}</Code>
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
                        onChange={(value) => setNetworkViewMode(value as 'text' | 'graph')}
                        data={[
                          { label: 'Text', value: 'text' },
                          { label: 'Graph', value: 'graph' },
                        ]}
                      />
                    </Group>
                    {networkViewMode === 'graph' ? (
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
                            <Badge size="xs" variant="light">{req.method}</Badge>
                            <Badge size="xs" color={req.status >= 200 && req.status < 300 ? 'teal' : 'red'}>
                              {req.status}
                            </Badge>
                          </Group>
                          <Text size="xs" c="dimmed">{req.duration}ms</Text>
                        </Group>
                        <Text size="sm" ff="monospace" truncate="end">{req.url}</Text>
                        <Text size="xs" c="dimmed" mt={4}>{formatTimestamp(req.timestamp, new Date(sessionData.startTime))}</Text>
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
                        onChange={(value) => setPerformanceViewMode(value as 'text' | 'graph')}
                        data={[
                          { label: 'Text', value: 'text' },
                          { label: 'Graph', value: 'graph' },
                        ]}
                      />
                    </Group>
                    {performanceViewMode === 'graph' ? (
                      <PerformanceVisualization
                        performance={sessionData.performance}
                      />
                    ) : (
                      <>
                    <Box>
                      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="xs">Interaction Metrics</Text>
                      <Table>
                        <Table.Thead>
                          <Table.Tr>
                            <Table.Th>Interaction</Table.Th>
                            <Table.Th>Duration</Table.Th>
                            <Table.Th>Apdex</Table.Th>
                          </Table.Tr>
                        </Table.Thead>
                        <Table.Tbody>
                          {sessionData.performance.interactionMetrics.map((metric) => (
                            <Table.Tr key={metric.interactionId}>
                              <Table.Td><Text size="sm">{metric.interactionName}</Text></Table.Td>
                              <Table.Td><Text size="sm">{metric.duration}ms</Text></Table.Td>
                              <Table.Td>
                                <Badge size="sm" color={metric.apdexScore >= 0.8 ? 'teal' : metric.apdexScore >= 0.5 ? 'yellow' : 'red'}>
                                  {metric.apdexScore.toFixed(2)}
                                </Badge>
                              </Table.Td>
                            </Table.Tr>
                          ))}
                        </Table.Tbody>
                      </Table>
                    </Box>
                    </>
                    )}
                  </Stack>
                </Tabs.Panel>
              </Box>
            </Tabs>
            )}
          </Paper>
        </Grid.Col>
      </Grid>

      {/* Flame Chart Timeline */}
      <Paper className={classes.timelineSection}>
        <Group justify="space-between" mb="md">
          <Text size="md" fw={600}>Session Timeline</Text>
          <Badge size="sm" variant="light" color="teal" leftSection={<IconBug size={12} />}>
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
      <DetailsSidebar
        item={selectedSpan}
        onClose={handleCloseSidebar}
      />
    </Box>
  );
};
