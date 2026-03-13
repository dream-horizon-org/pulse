import { Paper, Stack, Text, Group, Badge, SimpleGrid, Card, ThemeIcon, Timeline } from "@mantine/core";
import { IconCurrencyDollar } from "@tabler/icons-react";
import { SessionReplayMetrics } from "../../../services/sessionReplay/types";
import { DrillDownType } from "../../../contexts/SessionReplayFilterContext";
import classes from "./InsightsDashboard.module.css";
import { MetricCard, ComparisonMetricCard, StatusMetricCard } from "./MetricCards";
import { DrillDownButton, DualDrillDown } from "./DrillDownActions";

interface InsightsDashboardProps {
  metrics: SessionReplayMetrics;
  onViewSession?: (sessionId: string) => void;
  onDrillDown?: (type: DrillDownType, value: any, label: string) => void;
}

/**
 * Consistent Section Header Component
 */
interface SectionHeaderProps {
  title: string;
  description: string;
  action?: React.ReactNode;
}

function SectionHeader({ title, description, action }: SectionHeaderProps) {
  return (
    <Group justify="space-between" align="start" mb="lg">
      <Stack gap={4}>
        <Text size="lg" fw={600}>{title}</Text>
        <Text size="sm" c="dimmed">{description}</Text>
      </Stack>
      {action && <div>{action}</div>}
    </Group>
  );
}

export function InsightsDashboard({ metrics, onViewSession, onDrillDown }: InsightsDashboardProps) {
  const { 
    criticalInteractions, 
    topIssueHotspots, 
    topErrorPatterns, 
    comparison,
    estimatedImpact,
    timePatterns
  } = metrics;

  // Helper function for colors - using Pulse design system
  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'critical': return 'red';
      case 'high': return 'red';
      case 'medium': return 'yellow';
      case 'low': return 'teal';
      default: return 'gray';
    }
  };

  const getErrorTypeColor = (errorType: string) => {
    switch (errorType) {
      case 'crash': return 'red';
      case 'network': return 'red';
      case 'javascript': return 'yellow';
      case 'api': return 'red';
      case 'console': return 'gray';
      default: return 'gray';
    }
  };

  const getIssueTypeLabel = (issueType: string) => {
    const labels: Record<string, string> = {
      'rage_click': 'Rage Click',
      'dead_click': 'Dead Click',
      'slow_interaction': 'Slow Response',
      'form_abandon': 'Form Abandoned',
      'error': 'Error'
    };
    return labels[issueType] || issueType;
  };

  return (
    <Stack gap="lg" className={classes.dashboard}>
      {/* ========================================
          1. BUSINESS IMPACT (Optional - Top Priority)
          ======================================== */}
      {estimatedImpact && (estimatedImpact.totalRevenueAtRisk > 0 || estimatedImpact.affectedUsers > 0) && (
        <Paper p="lg" radius="md" id="business-impact">
          <Stack gap="lg">
            <SectionHeader
              title="Business Impact"
              description="Estimated business impact from interaction failures - immediate attention required"
            />

            <SimpleGrid cols={{ base: 1, sm: 2, md: 4 }} spacing="md">
              {estimatedImpact.totalRevenueAtRisk > 0 && (
                <MetricCard
                  label="Revenue at Risk"
                  value={`$${estimatedImpact.totalRevenueAtRisk.toLocaleString()}`}
                  description={`per ${estimatedImpact.revenueAtRiskPeriod}`}
                  icon={<IconCurrencyDollar size={16} />}
                  valueColor="red"
                  onClick={() => onDrillDown?.('sessions_with_issues', 'revenue_impact', 'Sessions with Revenue Impact')}
                />
              )}

              <MetricCard
                label="Affected Users"
                value={estimatedImpact.affectedUsers}
                description={`${estimatedImpact.affectedUsersPercentage.toFixed(1)}% of ${estimatedImpact.totalUsers.toLocaleString()}`}
                onClick={() => onDrillDown?.('affected_users', estimatedImpact.affectedUsers, 'Affected Users Sessions')}
              />

              {estimatedImpact.conversionImpact > 0 && (
                <MetricCard
                  label="Conversion Impact"
                  value={`-${estimatedImpact.conversionImpact.toFixed(1)}pp`}
                  description={`vs ${estimatedImpact.conversionBaseline.toFixed(1)}% baseline`}
                  valueColor="red"
                  onClick={() => onDrillDown?.('conversion_loss', estimatedImpact.conversionImpact, 'Conversion Loss Sessions')}
                />
              )}

              {estimatedImpact.supportTicketCorrelation && (
                <MetricCard
                  label="Support Tickets"
                  value={estimatedImpact.supportTicketCorrelation.count}
                  badge={
                    <Badge size="xs" color={
                      estimatedImpact.supportTicketCorrelation.confidence === 'high' ? 'teal' :
                      estimatedImpact.supportTicketCorrelation.confidence === 'medium' ? 'yellow' : 'gray'
                    }>
                      {estimatedImpact.supportTicketCorrelation.confidence} confidence
                    </Badge>
                  }
                  onClick={() => onDrillDown?.('sessions_with_issues', 'support_tickets', 'Sessions with Support Tickets')}
                />
              )}
            </SimpleGrid>
          </Stack>
        </Paper>
      )}

      {/* ========================================
          2. SESSION HEALTH - Universal Baseline
          ======================================== */}
      <Stack gap="lg" px="lg" id="session-health">
        <SectionHeader
          title="Session Health"
          description="Overall session quality across all interactions"
        />
        
        <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
          <ComparisonMetricCard
            label="Total Sessions"
            currentValue={comparison.totalSessions.current}
            changePercent={comparison.totalSessions.changePercent}
            comparisonLabel={`vs ${comparison.comparisonPeriod.label}`}
            onClick={() => onDrillDown?.('sessions_with_issues', 'all_sessions', 'All Sessions')}
            actionLabel="Click to view all sessions"
          />

          <ComparisonMetricCard
            label="Sessions with Failed Interactions"
            currentValue={comparison.sessionsWithIssues.current}
            changePercent={comparison.sessionsWithIssues.change}
            displayPercent={comparison.sessionsWithIssues.currentPercent}
            positiveIsGood={false}
            comparisonLabel={comparison.sessionsWithIssues.trend}
            onClick={() => onDrillDown?.('sessions_with_issues', 'all', 'Sessions with Failed Interactions')}
            actionLabel="Click to view failed sessions"
          />
        </SimpleGrid>
      </Stack>

      {/* ========================================
          3. CRITICAL INTERACTIONS (Conditional)
          ======================================== */}
      {criticalInteractions && criticalInteractions.length > 0 && (
        <Paper p="lg" radius="md" id="critical-interactions">
          <Stack gap="md">
            <SectionHeader
              title="Critical Interactions"
              description="Performance metrics for your configured critical user flows"
            />
            
            <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }} spacing="md">
              {criticalInteractions.map((interaction) => (
                <StatusMetricCard
                  key={interaction.interactionId}
                  label={interaction.displayName}
                  value={interaction.apdexScore}
                  status={interaction.healthStatus.toLowerCase() as 'excellent' | 'good' | 'fair' | 'poor'}
                  subMetrics={[
                    { label: 'Error Rate', value: `${interaction.errorRate.toFixed(1)}%` },
                    { label: 'P50', value: `${interaction.p50Latency}ms` },
                    { label: 'Sessions', value: interaction.sessionsWithThisInteraction }
                  ]}
                  progressValue={interaction.apdexScore * 100}
                  onClick={() => onDrillDown?.('interaction', interaction.interactionId, `${interaction.displayName} Sessions`)}
                  actionLabel="View Sessions"
                />
              ))}
            </SimpleGrid>
          </Stack>
        </Paper>
      )}

      {/* ========================================
          4. WHAT'S BREAKING - UX Friction + Technical Errors
          ======================================== */}
      <Paper p="lg" radius="md" id="whats-breaking">
        <Stack gap="md">
          <SectionHeader
            title="What's Breaking"
            description="UX friction points and technical errors affecting interactions"
          />

          <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
            {/* UX Friction Hotspots */}
            <Card padding="lg" radius="md" withBorder className={classes.insightCard}>
              <Stack gap="md">
                <div>
                  <Text size="md" fw={600}>UX Friction Hotspots</Text>
                  <Text size="xs" c="dimmed" mt={4}>Where users struggle with interactions (top 3)</Text>
                </div>

                {topIssueHotspots && topIssueHotspots.length > 0 ? (
                  <Timeline active={Math.min(topIssueHotspots.length, 3)} bulletSize={20} lineWidth={2}>
                    {topIssueHotspots.slice(0, 3).map((hotspot, index) => (
                      <Timeline.Item
                        key={index}
                        bullet={
                          <ThemeIcon
                            size={20}
                            variant="filled"
                            color={getSeverityColor(hotspot.severity)}
                            radius="xl"
                          >
                            <Text size="xs" fw={700}>{index + 1}</Text>
                          </ThemeIcon>
                        }
                        title={
                          <Group gap="xs">
                            <Text fw={600} size="sm">{hotspot.location}</Text>
                            <Badge size="xs" color={getSeverityColor(hotspot.severity)} variant="light">
                              {getIssueTypeLabel(hotspot.issueType)}
                            </Badge>
                          </Group>
                        }
                      >
                        <Stack gap="xs" mt="xs">
                          {hotspot.specificElement && (
                            <Text size="xs" c="dimmed">
                              <strong>Element:</strong> {hotspot.specificElement}
                            </Text>
                          )}
                          <Group gap="md">
                            <Text size="xs" c="dimmed">{hotspot.affectedSessions} sessions</Text>
                            <Text size="xs" c="dimmed">{hotspot.hitRate.toFixed(1)}% hit rate</Text>
                            <Text size="xs" c="dimmed">{hotspot.medianStruggleTime}s struggle</Text>
                          </Group>
                          <DrillDownButton
                            label="Watch sessions"
                            variant="subtle"
                            size="xs"
                            color={getSeverityColor(hotspot.severity)}
                            onClick={() => onDrillDown?.('friction_hotspot', hotspot.location, `Friction: ${hotspot.location}`)}
                          />
                        </Stack>
                      </Timeline.Item>
                    ))}
                  </Timeline>
                ) : (
                  <Text size="sm" c="dimmed" ta="center" py="xl">
                    No UX friction hotspots detected ✅
                  </Text>
                )}
              </Stack>
            </Card>

            {/* Technical Error Patterns */}
            <Card padding="lg" radius="md" withBorder className={classes.insightCard}>
              <Stack gap="md">
                <div>
                  <Text size="md" fw={600}>Technical Error Patterns</Text>
                  <Text size="xs" c="dimmed" mt={4}>Failed interactions by error type (top 3)</Text>
                </div>

                {topErrorPatterns && topErrorPatterns.length > 0 ? (
                  <Stack gap="md">
                    {topErrorPatterns.slice(0, 3).map((pattern, index) => (
                      <Card key={index} padding="md" radius="md" withBorder>
                        <Stack gap="xs">
                          <Group gap="xs">
                            <Badge size="sm" color={getErrorTypeColor(pattern.errorType)} variant="filled">
                              {pattern.errorType}
                            </Badge>
                            <Badge size="sm" color={getSeverityColor(pattern.severity)} variant="light">
                              {pattern.severity}
                            </Badge>
                          </Group>
                          <Text size="sm" fw={600} lineClamp={1}>{pattern.displayName}</Text>

                          <Group gap="md">
                            <Text size="xs" c="dimmed">
                              <strong>{pattern.count}x</strong> occurred
                            </Text>
                            <Text size="xs" c="dimmed">
                              <strong>{pattern.affectedSessions}</strong> sessions
                            </Text>
                            <Text size="xs" c="dimmed">
                              <strong>{pattern.uniqueUsers}</strong> users
                            </Text>
                          </Group>

                          {pattern.platformBreakdown && pattern.platformBreakdown.length > 0 && (
                            <Group gap="xs">
                              {pattern.platformBreakdown.map((platformObj) => (
                                <Badge key={platformObj.platform} size="xs" variant="dot" color="gray">
                                  {platformObj.platform} ({platformObj.count})
                                </Badge>
                              ))}
                            </Group>
                          )}

                          <Group gap="xs" grow>
                            <DualDrillDown
                              primaryLabel="Sample"
                              secondaryLabel="All"
                              secondaryCount={pattern.affectedSessions}
                              onPrimaryClick={() => onViewSession?.(pattern.sampleSessionId)}
                              onSecondaryClick={() => onDrillDown?.('error_pattern', pattern.errorSignature, `Error: ${pattern.displayName}`)}
                              color={getSeverityColor(pattern.severity)}
                            />
                          </Group>
                        </Stack>
                      </Card>
                    ))}
                  </Stack>
                ) : (
                  <Text size="sm" c="dimmed" ta="center" py="xl">
                    No technical errors detected ✅
                  </Text>
                )}
              </Stack>
            </Card>
          </SimpleGrid>
        </Stack>
      </Paper>

      {/* ========================================
          5. TIME-BASED PATTERNS (Optional)
          ======================================== */}
      {timePatterns && (timePatterns.peakErrorHours?.length > 0 || timePatterns.errorTrend?.length > 0) && (
        <Paper p="lg" radius="md" id="time-patterns">
          <Stack gap="md">
            <SectionHeader
              title="Time-Based Patterns"
              description="When interaction failures occur most frequently"
            />

            {timePatterns.peakErrorHours && timePatterns.peakErrorHours.length > 0 && (
              <Stack gap="sm">
                <Text size="sm" fw={600}>Peak Error Hours (Top 3)</Text>
                <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="md">
                  {timePatterns.peakErrorHours.slice(0, 3).map((hourPattern, idx) => (
                    <MetricCard
                      key={idx}
                      label={`${hourPattern.hour.toString().padStart(2, '0')}:00 - ${(hourPattern.hour + 1).toString().padStart(2, '0')}:00`}
                      value={`${hourPattern.errorCount} errors`}
                      description={`${hourPattern.errorRate.toFixed(1)}% error rate`}
                      valueColor="red"
                      onClick={() => onDrillDown?.('sessions_with_issues', { hour: hourPattern.hour }, `Errors at ${hourPattern.hour}:00`)}
                      padding="md"
                    />
                  ))}
                </SimpleGrid>
              </Stack>
            )}
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
