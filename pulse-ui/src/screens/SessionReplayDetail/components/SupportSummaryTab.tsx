/**
 * Support Summary Tab
 * 
 * PERSONA: Customer Support
 * GOAL: Help user fast - understand issue, take action
 * 
 * SHOWS:
 * - What broke? (plain language)
 * - Who else affected? (pattern recognition)
 * - Quick actions (create ticket, send workaround, escalate)
 * - User history (previous issues)
 * - Known issues (is there a workaround?)
 */

import { Stack, Card, Text, Group, Badge, Button, Alert, Divider, Timeline } from '@mantine/core';
import { 
  IconTicket, 
  IconRocket, 
  IconAlertTriangle, 
  IconUsers,
  IconBug,
  IconHistory,
  IconCheck,
  IconX,
  IconExclamationCircle
} from '@tabler/icons-react';
import { SessionDetailData, DetectedIssue } from '../../../services/sessionReplay/mockSessionDetail';

interface SupportSummaryTabProps {
  sessionData: SessionDetailData;
  detectedIssues: DetectedIssue[];
}

export const SupportSummaryTab: React.FC<SupportSummaryTabProps> = ({ sessionData, detectedIssues }) => {
  const criticalIssues = detectedIssues.filter(i => i.severity === 'critical' || i.severity === 'high');
  const hasKnownIssue = sessionData.supportContext?.matchesKnownIssue;
  
  return (
    <Stack gap="lg">
      {/* ISSUE QUICK FACTS */}
      <Card padding="md" withBorder>
        <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">Issue Quick Facts</Text>
        
        {criticalIssues.length > 0 ? (
          <Stack gap="md">
            {criticalIssues.map((issue, idx) => (
              <Alert
                key={idx}
                color={issue.severity === 'critical' ? 'red' : 'orange'}
                title={issue.title}
                icon={<IconAlertTriangle size={16} />}
              >
                <Text size="sm" mb="xs">{issue.userFacingImpact}</Text>
                <Group gap="xs">
                  <Badge size="sm" variant="light">
                    {issue.affectedFeature || 'Unknown Feature'}
                  </Badge>
                  <Badge size="sm" variant="light" color="gray">
                    {formatTimestamp(issue.timestamp)}
                  </Badge>
                </Group>
              </Alert>
            ))}
          </Stack>
        ) : (
          <Alert color="teal" title="No Critical Issues" icon={<IconCheck size={16} />}>
            <Text size="sm">Session completed without major errors. User was able to complete their actions successfully.</Text>
          </Alert>
        )}
      </Card>

      {/* CUSTOMER IMPACT */}
      <Card padding="md" withBorder>
        <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">Customer Impact</Text>
        
        <Stack gap="sm">
          <Group justify="space-between">
            <Text size="sm" c="dimmed">User Status</Text>
            <Badge color={sessionData.isAnonymous ? 'gray' : 'blue'}>
              {sessionData.isAnonymous ? 'Anonymous' : 'Identified'}
            </Badge>
          </Group>
          
          <Group justify="space-between">
            <Text size="sm" c="dimmed">User ID</Text>
            <Text size="sm" fw={500} ff="monospace">{sessionData.userId}</Text>
          </Group>
          
          <Group justify="space-between">
            <Text size="sm" c="dimmed">Session Duration</Text>
            <Text size="sm" fw={500}>{formatDuration(sessionData.duration)}</Text>
          </Group>
          
          <Group justify="space-between">
            <Text size="sm" c="dimmed">Session Quality</Text>
            <Badge color={getQualityColor(sessionData.interactionQuality)}>
              {sessionData.interactionQuality}/10
            </Badge>
          </Group>
          
          {sessionData.businessContext?.conversionValue && (
            <Group justify="space-between">
              <Text size="sm" c="dimmed">Attempted Transaction</Text>
              <Text size="sm" fw={600} c="red">
                ${sessionData.businessContext.conversionValue}
              </Text>
            </Group>
          )}
        </Stack>
      </Card>

      {/* SIMILAR ISSUES TODAY */}
      {sessionData.businessContext?.similarErrorsToday && sessionData.businessContext.similarErrorsToday > 0 && (
        <Card padding="md" withBorder>
          <Group justify="space-between" mb="md">
            <Text size="xs" tt="uppercase" fw={600} c="dimmed">Similar Issues Today</Text>
            <Badge size="lg" color="orange" variant="filled">
              {sessionData.businessContext.similarErrorsToday} Users Affected
            </Badge>
          </Group>
          
          <Alert color="orange" icon={<IconUsers size={16} />}>
            <Text size="sm">
              This is a <strong>pattern</strong>! {sessionData.businessContext.similarErrorsToday} other users 
              experienced the same issue today. Consider escalating to engineering.
            </Text>
          </Alert>
        </Card>
      )}

      {/* KNOWN ISSUES MATCH */}
      {hasKnownIssue && (
        <Card padding="md" withBorder>
          <Group justify="space-between" mb="md">
            <Text size="xs" tt="uppercase" fw={600} c="dimmed">Known Issue</Text>
            <Badge color="blue" variant="light">
              Issue #{hasKnownIssue.issueId.split('_')[2]}
            </Badge>
          </Group>
          
          <Alert color="blue" title={hasKnownIssue.title} icon={<IconBug size={16} />}>
            <Stack gap="sm">
              <Text size="sm">
                Affected Users: <strong>{hasKnownIssue.affectedUsers}</strong>
              </Text>
              <Text size="sm">
                Status: <Badge size="sm" color={hasKnownIssue.status === 'resolved' ? 'teal' : 'orange'}>
                  {hasKnownIssue.status.replace('_', ' ')}
                </Badge>
              </Text>
              {hasKnownIssue.workaround && (
                <>
                  <Divider />
                  <Text size="sm" fw={600}>Workaround Available:</Text>
                  <Text size="sm" c="dimmed">{hasKnownIssue.workaround}</Text>
                </>
              )}
            </Stack>
          </Alert>
        </Card>
      )}

      {/* QUICK ACTIONS */}
      <Card padding="md" withBorder style={{ position: 'sticky', bottom: 0, backgroundColor: 'var(--mantine-color-body)' }}>
        <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">Quick Actions</Text>
        
        <Stack gap="xs">
          {sessionData.supportContext?.suggestedActions.map((action) => (
            <Button
              key={action.id}
              fullWidth
              variant={action.priority === 'high' ? 'filled' : 'light'}
              color={action.priority === 'high' ? 'blue' : 'gray'}
              leftSection={
                action.type === 'create_ticket' ? <IconTicket size={16} /> :
                action.type === 'send_workaround' ? <IconRocket size={16} /> :
                <IconExclamationCircle size={16} />
              }
            >
              {action.label}
            </Button>
          ))}
        </Stack>
      </Card>

      {/* USER HISTORY */}
      {sessionData.supportContext?.previousIssues && sessionData.supportContext.previousIssues.length > 0 && (
        <Card padding="md" withBorder>
          <Group mb="md">
            <IconHistory size={18} />
            <Text size="xs" tt="uppercase" fw={600} c="dimmed">Previous Issues</Text>
          </Group>
          
          <Timeline bulletSize={16} lineWidth={2}>
            {sessionData.supportContext.previousIssues.map((issue, idx) => (
              <Timeline.Item
                key={idx}
                bullet={issue.resolved ? <IconCheck size={12} /> : <IconX size={12} />}
                color={issue.resolved ? 'teal' : 'red'}
              >
                <Text size="sm" fw={500}>{issue.issueType}</Text>
                <Text size="xs" c="dimmed">{new Date(issue.timestamp).toLocaleDateString()}</Text>
                <Badge size="xs" color={issue.resolved ? 'teal' : 'red'} mt={4}>
                  {issue.resolved ? 'Resolved' : 'Unresolved'}
                </Badge>
              </Timeline.Item>
            ))}
          </Timeline>
        </Card>
      )}
    </Stack>
  );
};

// Helper functions
function formatTimestamp(ms: number): string {
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}m ${remainingSeconds}s`;
}

function formatDuration(ms: number): string {
  if (ms < 60000) return `${Math.round(ms / 1000)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
}

function getQualityColor(score: number): string {
  if (score >= 8) return 'teal';
  if (score >= 6) return 'yellow';
  return 'red';
}
