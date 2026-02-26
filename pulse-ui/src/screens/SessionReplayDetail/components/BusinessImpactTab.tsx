/**
 * Business Impact Tab
 * 
 * PERSONA: Product Manager
 * GOAL: Understand business impact, identify patterns, make decisions
 * 
 * SHOWS:
 * - Conversion status (did they complete goal?)
 * - Revenue impact ($ lost if failed)
 * - Journey timing (where did they spend time?)
 * - A/B test assignment (which variant?)
 * - Feature usage (engagement breakdown)
 * - Comparison metrics (vs successful sessions)
 */

import { Stack, Card, Text, Group, Badge, Progress, SimpleGrid, Alert, Divider, Button, RingProgress } from '@mantine/core';
import { 
  IconChartLine, 
  IconCoin,
  IconClock,
  IconChevronRight,
  IconUsers,
  IconFlask,
  IconTarget,
  IconTrendingDown,
  IconTrendingUp,
  IconAlertCircle
} from '@tabler/icons-react';
import { SessionDetailData } from '../../../services/sessionReplay/mockSessionDetail';

interface BusinessImpactTabProps {
  sessionData: SessionDetailData;
}

export const BusinessImpactTab: React.FC<BusinessImpactTabProps> = ({ sessionData }) => {
  const { businessContext, sessionIntent } = sessionData;
  
  if (!businessContext) {
    return (
      <Alert color="gray" icon={<IconAlertCircle size={16} />}>
        <Text size="sm">No business context available for this session.</Text>
      </Alert>
    );
  }

  const completionRate = sessionIntent?.completed ? 100 : 0;
  const expectedDuration = sessionIntent?.expectedDuration || 120000;
  const actualDuration = sessionIntent?.actualDuration || sessionData.duration;
  const durationDiff = ((actualDuration - expectedDuration) / expectedDuration) * 100;
  
  return (
    <Stack gap="lg">
      {/* CONVERSION STATUS */}
      <Card padding="md" withBorder>
        <Group justify="space-between" mb="md">
          <Text size="xs" tt="uppercase" fw={600} c="dimmed">Conversion Status</Text>
          <Badge
            size="lg"
            color={sessionIntent?.completed ? 'teal' : 'red'}
            leftSection={sessionIntent?.completed ? <IconTrendingUp size={14} /> : <IconTrendingDown size={14} />}
          >
            {sessionIntent?.completed ? 'Completed' : 'Abandoned'}
          </Badge>
        </Group>

        {businessContext.isConversionSession && (
          <Stack gap="sm">
            <Group justify="space-between">
              <Text size="sm" c="dimmed">Goal</Text>
              <Text size="sm" fw={600}>{businessContext.conversionGoal}</Text>
            </Group>
            
            <Group justify="space-between">
              <Text size="sm" c="dimmed">Stage</Text>
              <Badge color="violet" variant="light">
                {businessContext.conversionStage}
              </Badge>
            </Group>
            
            {businessContext.funnelStep && (
              <Group justify="space-between">
                <Text size="sm" c="dimmed">Funnel Progress</Text>
                <Text size="sm" fw={500}>{businessContext.funnelStep}</Text>
              </Group>
            )}
            
            {businessContext.conversionValue && (
              <>
                <Divider />
                <Group justify="space-between">
                  <Text size="sm" c="dimmed">Transaction Value</Text>
                  <Text size="lg" fw={700} c={sessionIntent?.completed ? 'teal' : 'red'}>
                    ${businessContext.conversionValue.toFixed(2)}
                  </Text>
                </Group>
              </>
            )}
            
            {sessionIntent?.abandonedAt && (
              <Alert color="red" mt="sm">
                <Text size="sm">
                  Abandoned at: <strong>{sessionIntent.abandonedAt}</strong>
                </Text>
              </Alert>
            )}
          </Stack>
        )}
      </Card>

      {/* JOURNEY TIMING */}
      <Card padding="md" withBorder>
        <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">Journey Timing</Text>
        
        <Stack gap="md">
          <Group justify="space-between">
            <Group gap="xs">
              <IconClock size={16} />
              <Text size="sm">Actual Duration</Text>
            </Group>
            <Text size="sm" fw={600}>{formatDuration(actualDuration)}</Text>
          </Group>
          
          <Group justify="space-between">
            <Text size="sm" c="dimmed">Expected Duration</Text>
            <Text size="sm" c="dimmed">{formatDuration(expectedDuration)}</Text>
          </Group>
          
          <Progress
            value={Math.min((actualDuration / expectedDuration) * 100, 200)}
            color={durationDiff > 50 ? 'red' : durationDiff > 20 ? 'yellow' : 'teal'}
            size="lg"
          />
          
          {Math.abs(durationDiff) > 10 && (
            <Text size="xs" c={durationDiff > 0 ? 'red' : 'teal'}>
              {durationDiff > 0 ? '+' : ''}{durationDiff.toFixed(0)}% {durationDiff > 0 ? 'slower' : 'faster'} than expected
            </Text>
          )}
          
          {/* Journey Path */}
          <Divider />
          <Text size="xs" tt="uppercase" fw={600} c="dimmed">User Journey</Text>
          <Group gap={4} wrap="nowrap" style={{ overflowX: 'auto' }}>
            {sessionData.journey.map((path, idx) => (
              <Group key={idx} gap={4} wrap="nowrap">
                <Badge
                  variant={idx === sessionData.journey.length - 1 ? 'filled' : 'light'}
                  color={path.includes('error') ? 'red' : 'blue'}
                  size="sm"
                >
                  {path}
                </Badge>
                {idx < sessionData.journey.length - 1 && <IconChevronRight size={12} color="gray" />}
              </Group>
            ))}
          </Group>
        </Stack>
      </Card>

      {/* PATTERN DETECTION */}
      {(businessContext.similarSessionsCount || businessContext.similarErrorsToday) && (
        <Card padding="md" withBorder>
          <Group mb="md">
            <IconUsers size={18} />
            <Text size="xs" tt="uppercase" fw={600} c="dimmed">Pattern Detection</Text>
          </Group>
          
          <SimpleGrid cols={2} spacing="md">
            {businessContext.similarSessionsCount && (
              <Card padding="sm" withBorder>
                <RingProgress
                  size={80}
                  thickness={8}
                  sections={[{ value: 100, color: 'violet' }]}
                  label={
                    <Text size="lg" fw={700} ta="center">{businessContext.similarSessionsCount}</Text>
                  }
                  mb="xs"
                />
                <Text size="xs" ta="center" c="dimmed">Similar Sessions Today</Text>
              </Card>
            )}
            
            {businessContext.similarErrorsToday && businessContext.similarErrorsToday > 0 && (
              <Card padding="sm" withBorder>
                <RingProgress
                  size={80}
                  thickness={8}
                  sections={[{ value: 100, color: 'red' }]}
                  label={
                    <Text size="lg" fw={700} ta="center">{businessContext.similarErrorsToday}</Text>
                  }
                  mb="xs"
                />
                <Text size="xs" ta="center" c="dimmed">Same Error Today</Text>
              </Card>
            )}
          </SimpleGrid>
          
          <Button variant="light" fullWidth mt="md" leftSection={<IconTarget size={16} />}>
            View Similar Sessions
          </Button>
        </Card>
      )}

      {/* USER SEGMENT */}
      <Card padding="md" withBorder>
        <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">User Segmentation</Text>
        
        <Stack gap="sm">
          <Group justify="space-between">
            <Text size="sm" c="dimmed">Segment</Text>
            <Badge color="blue" variant="light">
              {businessContext.userSegment || 'Unknown'}
            </Badge>
          </Group>
          
          {businessContext.cohort && (
            <Group justify="space-between">
              <Text size="sm" c="dimmed">Cohort</Text>
              <Text size="sm" fw={500}>{businessContext.cohort}</Text>
            </Group>
          )}
          
          <Group justify="space-between">
            <Text size="sm" c="dimmed">Session Type</Text>
            <Badge color={businessContext.isFirstSession ? 'orange' : 'teal'}>
              {businessContext.isFirstSession ? 'First Session' : 'Returning User'}
            </Badge>
          </Group>
          
          {businessContext.lifetimeValue !== undefined && (
            <Group justify="space-between">
              <Text size="sm" c="dimmed">Lifetime Value</Text>
              <Text size="sm" fw={600}>${businessContext.lifetimeValue.toFixed(2)}</Text>
            </Group>
          )}
        </Stack>
      </Card>

      {/* A/B TESTS */}
      {businessContext.experiments && businessContext.experiments.length > 0 && (
        <Card padding="md" withBorder>
          <Group mb="md">
            <IconFlask size={18} />
            <Text size="xs" tt="uppercase" fw={600} c="dimmed">A/B Test Assignment</Text>
          </Group>
          
          <Stack gap="sm">
            {businessContext.experiments.map((exp, idx) => (
              <Group key={idx} justify="space-between">
                <Text size="sm">{exp.name}</Text>
                <Badge color="violet" variant="filled">{exp.variant}</Badge>
              </Group>
            ))}
          </Stack>
          
          <Alert color="violet" mt="md" icon={<IconFlask size={16} />}>
            <Text size="sm">
              This user was in <strong>{businessContext.experiments[0].variant}</strong>. 
              Compare performance against control group.
            </Text>
          </Alert>
        </Card>
      )}

      {/* FEATURE ENGAGEMENT */}
      {businessContext.featuresUsed && businessContext.featuresUsed.length > 0 && (
        <Card padding="md" withBorder>
          <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">Feature Engagement</Text>
          
          <Stack gap="sm">
            {businessContext.featuresUsed.map((feature, idx) => {
              const engagementTime = businessContext.featureEngagement?.[feature] || 0;
              const percentage = (engagementTime / sessionData.duration) * 100;
              
              return (
                <div key={idx}>
                  <Group justify="space-between" mb={4}>
                    <Text size="sm">{feature}</Text>
                    <Text size="xs" c="dimmed">{formatDuration(engagementTime)}</Text>
                  </Group>
                  <Progress value={percentage} color="teal" size="sm" />
                </div>
              );
            })}
          </Stack>
        </Card>
      )}

      {/* QUICK ACTIONS */}
      <Card padding="md" withBorder style={{ position: 'sticky', bottom: 0, backgroundColor: 'var(--mantine-color-body)' }}>
        <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">Product Actions</Text>
        
        <Stack gap="xs">
          <Button variant="filled" color="violet" leftSection={<IconChartLine size={16} />}>
            Create Funnel Analysis
          </Button>
          <Button variant="light" leftSection={<IconTarget size={16} />}>
            Find Similar Drop-offs
          </Button>
          <Button variant="light">
            Add to Watch List
          </Button>
        </Stack>
      </Card>
    </Stack>
  );
};

// Helper function
function formatDuration(ms: number): string {
  if (ms < 60000) return `${Math.round(ms / 1000)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
}
