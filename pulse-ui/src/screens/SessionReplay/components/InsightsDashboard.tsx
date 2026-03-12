import { Paper, Stack, Text, Group, Badge, Divider, SimpleGrid, Button, Card, Progress, ThemeIcon, Timeline, Box } from "@mantine/core";
import { IconTrendingDown, IconTrendingUp, IconAlertCircle } from "@tabler/icons-react";
import { SessionReplayMetrics } from "../../../services/sessionReplay/types";
import classes from "./InsightsDashboard.module.css";

interface InsightsDashboardProps {
  metrics: SessionReplayMetrics;
  onViewSession?: (flowId: string) => void;
}

export function InsightsDashboard({ metrics, onViewSession }: InsightsDashboardProps) {
  const { criticalInteractions, estimatedImpact, topIssueHotspots, topErrorPatterns, comparison } = metrics;

  const getHealthColor = (healthStatus: string) => {
    switch (healthStatus) {
      case 'Excellent': return 'green';
      case 'Good': return 'yellow';
      case 'Fair': return 'orange';
      case 'Poor': return 'red';
      default: return 'gray';
    }
  };

  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'critical': return 'red';
      case 'high': return 'orange';
      case 'medium': return 'yellow';
      default: return 'gray';
    }
  };

  const getErrorTypeColor = (errorType: string) => {
    switch (errorType) {
      case 'crash': return 'red';
      case 'network': return 'orange';
      case 'javascript': return 'yellow';
      case 'console': return 'gray';
      default: return 'gray';
    }
  };

  return (
    <Stack gap="lg" className={classes.dashboard}>
      {/* Summary - The "So What?" */}
      <Paper p="xl" radius="md" className={classes.executiveSummary}>
        <Stack gap="lg">
          <div>
            <Text size="xl" fw={700} mb={4}>Summary</Text>
            <Text size="sm" c="dimmed">Your users are facing issues. Here's what matters most right now.</Text>
          </div>
          
          {/* Critical Alert */}
          {estimatedImpact.totalRevenueAtRisk > 0 && (
            <Paper p="md" radius="md" className={classes.criticalAlert}>
              <Group justify="space-between" wrap="nowrap">
                <Group gap="md">
                  <ThemeIcon size="xl" color="red" variant="light">
                    <IconAlertCircle size={28} />
                  </ThemeIcon>
                  <div>
                    <Text size="lg" fw={700} c="red">
                      ${estimatedImpact.totalRevenueAtRisk.toLocaleString()}/{estimatedImpact.revenueAtRiskPeriod} Revenue at Risk
                    </Text>
                    <Text size="sm" c="dimmed">
                      Payment flow failures impacting your revenue
                    </Text>
                  </div>
                </Group>
                <Button
                  variant="filled"
                  color="red"
                  size="md"
                  onClick={() => onViewSession?.('payment_flow')}
                >
                  Watch Worst Session
                </Button>
              </Group>
            </Paper>
          )}

          {/* Key Metrics Grid */}
          <SimpleGrid cols={{ base: 2, md: 3 }} spacing="lg">
            {/* Row 1: Impact Metrics */}
            <Card padding="md" radius="md" className={classes.metricCard}>
              <Stack gap="xs">
                <Text size="xs" tt="uppercase" fw={600} c="dimmed">Conversion Loss</Text>
                <Text size="xxl" fw={700} c="red">{estimatedImpact.conversionImpact}pp</Text>
                <Text size="xs" c="dimmed">vs {estimatedImpact.conversionBaseline}% baseline</Text>
              </Stack>
            </Card>

            <Card padding="md" radius="md" className={classes.metricCard}>
              <Stack gap="xs">
                <Text size="xs" tt="uppercase" fw={600} c="dimmed">Affected Users</Text>
                <Text size="xxl" fw={700}>{estimatedImpact.affectedUsers}</Text>
                <Text size="xs" c="dimmed">of {estimatedImpact.totalUsers} ({estimatedImpact.affectedUsersPercentage.toFixed(1)}%)</Text>
              </Stack>
            </Card>

            <Card padding="md" radius="md" className={classes.metricCard}>
              <Stack gap="xs">
                <Text size="xs" tt="uppercase" fw={600} c="dimmed">Sessions with Issues</Text>
                <Group gap="xs" align="baseline">
                  <Text size="xxl" fw={700}>
                    {comparison.sessionsWithIssues.current}
                  </Text>
                  <Text size="md" c="dimmed">({comparison.sessionsWithIssues.currentPercent.toFixed(1)}%)</Text>
                </Group>
                <Badge
                  size="sm"
                  color={comparison.sessionsWithIssues.trend === 'improving' ? "teal" : "red"}
                  variant="light"
                  leftSection={comparison.sessionsWithIssues.trend === 'improving' ? <IconTrendingUp size={12} /> : <IconTrendingDown size={12} />}
                >
                  {comparison.sessionsWithIssues.change > 0 ? "+" : ""}{comparison.sessionsWithIssues.change.toFixed(1)}pp
                </Badge>
              </Stack>
            </Card>

            {/* Row 2: Context & Evidence */}
            <Card padding="md" radius="md" className={classes.metricCard}>
              <Stack gap="xs">
                <Text size="xs" tt="uppercase" fw={600} c="dimmed">Related Tickets</Text>
                <Text size="xxl" fw={700}>
                  {estimatedImpact.supportTicketCorrelation?.count || 0}
                </Text>
                <Text size="xs" c="dimmed">
                  of {estimatedImpact.supportTicketCorrelation?.totalTickets || 0} total ({estimatedImpact.supportTicketCorrelation?.confidence || 'unknown'})
                </Text>
              </Stack>
            </Card>

            <Card padding="md" radius="md" className={classes.metricCard}>
              <Stack gap="xs">
                <Text size="xs" tt="uppercase" fw={600} c="dimmed">Total Sessions</Text>
                <Text size="xxl" fw={700}>{comparison.totalSessions.current}</Text>
                <Text size="xs" c="dimmed">{comparison.currentPeriod.label}</Text>
              </Stack>
            </Card>
          </SimpleGrid>
        </Stack>
      </Paper>

      {/* Critical Interaction Performance - Aligned with Pulse */}
      <Paper p="lg" radius="md">
        <Stack gap="md">
          <div>
            <Text size="lg" fw={700} mb={4}>Critical Interaction Performance</Text>
            <Text size="sm" c="dimmed">Real-time metrics from your configured critical interactions</Text>
          </div>
          
          <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }} spacing="md">
            {criticalInteractions.map((interaction) => (
              <Card key={interaction.interactionId} padding="lg" radius="md" withBorder className={classes.flowCard}>
                <Stack gap="md">
                  {/* Header */}
                  <Group justify="space-between" wrap="nowrap">
                    <Badge size="lg" color={getHealthColor(interaction.healthStatus)} variant="filled">
                      {interaction.displayName}
                    </Badge>
                    <Badge size="md" color={getHealthColor(interaction.healthStatus)} variant="light">
                      {interaction.healthStatus}
                    </Badge>
                  </Group>

                  {/* Apdex Score (Large) */}
                  <div style={{ textAlign: 'center', padding: '1rem 0' }}>
                    <Text size="3rem" fw={700} lh={1} style={{ color: `var(--mantine-color-${getHealthColor(interaction.healthStatus)}-6)` }}>
                      {interaction.apdexScore.toFixed(2)}
                    </Text>
                    <Text size="sm" c="dimmed" mt={4}>Apdex Score</Text>
                  </div>

                  {/* Progress Bar */}
                  <Progress 
                    value={interaction.apdexScore * 100}
                    size="lg" 
                    radius="md"
                    color={getHealthColor(interaction.healthStatus)}
                  />

                  {/* Metrics Grid */}
                  <SimpleGrid cols={2} spacing="xs">
                    <div>
                      <Text size="xs" c="dimmed">Error Rate</Text>
                      <Text size="sm" fw={600}>{interaction.errorRate.toFixed(1)}%</Text>
                    </div>
                    <div>
                      <Text size="xs" c="dimmed">P50 Latency</Text>
                      <Text size="sm" fw={600}>{interaction.p50Latency}ms</Text>
                    </div>
                    <div>
                      <Text size="xs" c="dimmed">Poor Users</Text>
                      <Text size="sm" fw={600}>{interaction.poorUserPercentage.toFixed(1)}%</Text>
                    </div>
                    <div>
                      <Text size="xs" c="dimmed">Sessions</Text>
                      <Text size="sm" fw={600}>{interaction.sessionsWithThisInteraction}</Text>
                    </div>
                  </SimpleGrid>

                  {/* Business Impact */}
                  {interaction.estimatedLoss && (
                    <Paper p="xs" radius="sm" style={{ background: 'var(--mantine-color-red-0)', border: '1px solid var(--mantine-color-red-2)' }}>
                      <Text size="xs" fw={600} c="red" ta="center">
                        {interaction.estimatedLoss.type === 'revenue' && interaction.estimatedLoss.unit}
                        {interaction.estimatedLoss.amount.toLocaleString()}
                        {interaction.estimatedLoss.type !== 'revenue' && ` ${interaction.estimatedLoss.unit}`} at risk/{interaction.estimatedLoss.period}
                      </Text>
                    </Paper>
                  )}

                  {/* Action Button */}
                  <Button
                    variant="light"
                    size="sm"
                    fullWidth
                    color={getHealthColor(interaction.healthStatus)}
                    onClick={() => onViewSession?.(interaction.interactionName)}
                  >
                    View Sessions
                  </Button>
                </Stack>
              </Card>
            ))}
          </SimpleGrid>
        </Stack>
      </Paper>

      {/* Session-Level Insights Grid - For All Personas */}
      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
        {/* UX Friction Points - For UX/Design Persona */}
        <Paper p="lg" radius="md" className={classes.insightCard}>
          <Stack gap="md">
            <div>
              <Text size="lg" fw={700}>Top Friction Hotspots</Text>
              <Text size="sm" c="dimmed">Where users struggle most (showing top 3)</Text>
            </div>

            <Timeline active={Math.min(topIssueHotspots.length, 3)} bulletSize={24} lineWidth={2}>
              {topIssueHotspots.slice(0, 3).map((hotspot, index) => (
                <Timeline.Item
                  key={index}
                  bullet={
                    <ThemeIcon
                      size={24}
                      variant="filled"
                      color={getSeverityColor(hotspot.severity)}
                      radius="xl"
                    >
                      {index + 1}
                    </ThemeIcon>
                  }
                  title={
                    <Group gap="xs">
                      <Text fw={600} size="sm">{hotspot.location}</Text>
                      <Badge size="sm" color={getSeverityColor(hotspot.severity)} variant="light">
                        {hotspot.issueType.replace('_', ' ')}
                      </Badge>
                    </Group>
                  }
                >
                  <Stack gap="xs" mt={4}>
                    {hotspot.specificElement && (
                      <Text size="sm" c="dimmed">
                        <strong>Element:</strong> {hotspot.specificElement}
                        {hotspot.elementIdentifier && <Text size="xs" c="dimmed">({hotspot.elementIdentifier})</Text>}
                      </Text>
                    )}
                    <Group gap="md">
                      <Text size="xs" c="dimmed">{hotspot.affectedSessions} sessions ({hotspot.hitRate.toFixed(1)}% hit rate)</Text>
                      <Text size="xs" c="dimmed">{hotspot.medianStruggleTime}s median struggle</Text>
                    </Group>
                    <Button
                      variant="subtle"
                      size="xs"
                      color={getSeverityColor(hotspot.severity)}
                      onClick={() => onViewSession?.(hotspot.location)}
                    >
                      Watch struggle sessions
                    </Button>
                  </Stack>
                </Timeline.Item>
              ))}
            </Timeline>
          </Stack>
        </Paper>

        {/* Error Patterns - For Tech/Support Persona */}
        <Paper p="lg" radius="md" className={classes.insightCard}>
          <Stack gap="md">
            <div>
              <Text size="lg" fw={700}>Top Error Patterns</Text>
              <Text size="sm" c="dimmed">Most common errors to investigate (showing top 3)</Text>
            </div>

            <Stack gap="sm">
              {topErrorPatterns.slice(0, 3).map((pattern, index) => (
                <Card key={index} padding="md" radius="md" withBorder>
                  <Stack gap="xs">
                    <Group justify="space-between" align="start">
                      <div style={{ flex: 1 }}>
                        <Group gap="xs" mb="xs">
                          <Badge size="sm" color={getErrorTypeColor(pattern.errorType)} variant="filled">
                            {pattern.errorType}
                          </Badge>
                          <Badge size="sm" color={getSeverityColor(pattern.severity)} variant="light">
                            {pattern.severity}
                          </Badge>
                        </Group>
                        <Text size="sm" fw={600} c="red">{pattern.displayName}</Text>
                      </div>
                    </Group>

                    <Group gap="md">
                      <Text size="xs" c="dimmed">
                        <strong>{pattern.count}x</strong> occurrences
                      </Text>
                      <Text size="xs" c="dimmed">
                        <strong>{pattern.affectedSessions}</strong> sessions
                      </Text>
                      <Text size="xs" c="dimmed">
                        <strong>{pattern.uniqueUsers}</strong> users
                      </Text>
                    </Group>

                    <Group gap="xs">
                      {pattern.platformBreakdown.map((platformObj) => (
                        <Badge key={platformObj.platform} size="xs" variant="dot" color="gray">
                          {platformObj.platform} ({platformObj.count})
                        </Badge>
                      ))}
                    </Group>

                    <Button
                      variant="subtle"
                      size="xs"
                      color={getSeverityColor(pattern.severity)}
                      onClick={() => onViewSession?.(pattern.sampleSessionId)}
                    >
                      Watch 1 sample session
                    </Button>
                  </Stack>
                </Card>
              ))}
            </Stack>
          </Stack>
        </Paper>
      </SimpleGrid>

      {/* Bottom Divider Before Session List */}
      <Divider mt="md" mb="xl" label="Session Evidence" labelPosition="center" />
    </Stack>
  );
}
