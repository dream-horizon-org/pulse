/**
 * Technical Tab
 * 
 * PERSONA: Engineering/Tech Team
 * GOAL: Find root cause, reproduce, ship fix
 * 
 * SHOWS:
 * - Root cause analysis (error chain)
 * - Code references (file:line with GitHub links)
 * - Error grouping (how many others?)
 * - Related PRs & Issues
 * - Reproducibility steps
 * - Environment details (version, flags, build)
 */

import { Stack, Card, Text, Group, Badge, Code, Alert, Divider, Button, Timeline, CopyButton, ActionIcon, Tooltip } from '@mantine/core';
import { 
  IconCode, 
  IconBug,
  IconGitBranch,
  IconAlertCircle,
  IconChecklist,
  IconSettings,
  IconCopy,
  IconBrandGithub,
  IconExternalLink,
  IconCheck,
  IconArrowRight
} from '@tabler/icons-react';
import { SessionDetailData, DetectedIssue } from '../../../services/sessionReplay/mockSessionDetail';

interface TechnicalTabProps {
  sessionData: SessionDetailData;
  detectedIssues: DetectedIssue[];
}

export const TechnicalTab: React.FC<TechnicalTabProps> = ({ sessionData, detectedIssues }) => {
  const { technicalContext } = sessionData;
  
  if (!technicalContext) {
    return (
      <Alert color="gray" icon={<IconAlertCircle size={16} />}>
        <Text size="sm">No technical context available for this session.</Text>
      </Alert>
    );
  }

  const hasErrors = detectedIssues.length > 0;
  const rootCause = technicalContext.rootCause;
  
  return (
    <Stack gap="lg">
      {/* ROOT CAUSE ANALYSIS */}
      {rootCause && (
        <Card padding="md" withBorder>
          <Group justify="space-between" mb="md">
            <Text size="xs" tt="uppercase" fw={600} c="dimmed">Root Cause Analysis</Text>
            <Badge color="orange" leftSection={<IconBug size={14} />}>
              {rootCause.type.replace('_', ' ')}
            </Badge>
          </Group>
          
          <Alert color="orange" title={`${rootCause.component} - ${rootCause.type}`} mb="md">
            <Text size="sm">{detectedIssues[0]?.technicalCause || 'Error detected'}</Text>
          </Alert>
          
          {/* Error Chain */}
          {rootCause.errorChain && rootCause.errorChain.length > 0 && (
            <>
              <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="sm">Error Propagation Chain</Text>
              <Timeline bulletSize={20} lineWidth={2}>
                {rootCause.errorChain.map((link, idx) => (
                  <Timeline.Item
                    key={idx}
                    bullet={<IconArrowRight size={12} />}
                    color="red"
                  >
                    <Text size="sm" fw={600}>{link.component}</Text>
                    <Code block mt={4}>{link.error}</Code>
                    <Text size="xs" c="dimmed" mt={2}>{formatTimestamp(link.timestamp)}</Text>
                  </Timeline.Item>
                ))}
              </Timeline>
            </>
          )}
        </Card>
      )}

      {/* CODE REFERENCES */}
      {technicalContext.codeReferences && technicalContext.codeReferences.length > 0 && (
        <Card padding="md" withBorder>
          <Group justify="space-between" mb="md">
            <Group gap="xs">
              <IconCode size={18} />
              <Text size="xs" tt="uppercase" fw={600} c="dimmed">Code References</Text>
            </Group>
          </Group>
          
          <Stack gap="md">
            {technicalContext.codeReferences.map((ref, idx) => (
              <Card key={idx} padding="sm" withBorder>
                <Group justify="space-between" mb="xs">
                  <Text size="sm" fw={600} ff="monospace">{ref.file}:{ref.line}</Text>
                  {ref.githubUrl && (
                    <Button
                      size="xs"
                      variant="light"
                      leftSection={<IconBrandGithub size={14} />}
                      component="a"
                      href={ref.githubUrl}
                      target="_blank"
                    >
                      View in GitHub
                    </Button>
                  )}
                </Group>
                
                <Text size="sm" c="dimmed" mb={4}>Function: <Code>{ref.function}</Code></Text>
                
                {ref.stackFrame && (
                  <Group gap="xs">
                    <Code style={{ flex: 1, fontSize: '11px' }}>{ref.stackFrame}</Code>
                    <CopyButton value={ref.stackFrame}>
                      {({ copied, copy }) => (
                        <Tooltip label={copied ? 'Copied' : 'Copy'}>
                          <ActionIcon color={copied ? 'teal' : 'gray'} onClick={copy} size="sm">
                            {copied ? <IconCheck size={14} /> : <IconCopy size={14} />}
                          </ActionIcon>
                        </Tooltip>
                      )}
                    </CopyButton>
                  </Group>
                )}
              </Card>
            ))}
          </Stack>
        </Card>
      )}

      {/* ERROR GROUP INFO */}
      {technicalContext.errorGroupInfo && (
        <Card padding="md" withBorder>
          <Group justify="space-between" mb="md">
            <Text size="xs" tt="uppercase" fw={600} c="dimmed">Error Group</Text>
            <Badge color="red" variant="filled">
              #{technicalContext.errorGroupInfo.groupId.split('_')[2]}
            </Badge>
          </Group>
          
          <Stack gap="sm">
            <Group justify="space-between">
              <Text size="sm" c="dimmed">Occurrences</Text>
              <Text size="sm" fw={600}>{technicalContext.errorGroupInfo.occurrenceCount}</Text>
            </Group>
            
            <Group justify="space-between">
              <Text size="sm" c="dimmed">Affected Users</Text>
              <Text size="sm" fw={600}>{technicalContext.errorGroupInfo.affectedUsers}</Text>
            </Group>
            
            <Group justify="space-between">
              <Text size="sm" c="dimmed">First Seen</Text>
              <Text size="sm">{new Date(technicalContext.errorGroupInfo.firstSeen).toLocaleString()}</Text>
            </Group>
            
            <Group justify="space-between">
              <Text size="sm" c="dimmed">Trend</Text>
              <Badge color={
                technicalContext.errorGroupInfo.trend === 'increasing' ? 'red' :
                technicalContext.errorGroupInfo.trend === 'decreasing' ? 'teal' : 'gray'
              }>
                {technicalContext.errorGroupInfo.trend}
              </Badge>
            </Group>
          </Stack>
          
          <Button variant="light" fullWidth mt="md" leftSection={<IconExternalLink size={16} />}>
            View All Occurrences
          </Button>
        </Card>
      )}

      {/* RELATED ISSUES & PRS */}
      {(technicalContext.relatedPRs && technicalContext.relatedPRs.length > 0) || 
       (technicalContext.relatedJiraIssues && technicalContext.relatedJiraIssues.length > 0) && (
        <Card padding="md" withBorder>
          <Group mb="md">
            <IconGitBranch size={18} />
            <Text size="xs" tt="uppercase" fw={600} c="dimmed">Related Issues & PRs</Text>
          </Group>
          
          <Stack gap="md">
            {technicalContext.relatedPRs?.map((pr, idx) => (
              <Card key={idx} padding="sm" withBorder>
                <Group justify="space-between">
                  <Group gap="xs">
                    <IconBrandGithub size={16} />
                    <Text size="sm" fw={500}>{pr.id}</Text>
                  </Group>
                  <Badge color={pr.status === 'merged' ? 'teal' : pr.status === 'open' ? 'blue' : 'gray'}>
                    {pr.status}
                  </Badge>
                </Group>
                <Text size="sm" c="dimmed" mt={4}>{pr.title}</Text>
                {pr.mergedAt && (
                  <Text size="xs" c="dimmed" mt={4}>
                    Merged {new Date(pr.mergedAt).toLocaleDateString()}
                    {isRecent(pr.mergedAt) && <Badge size="xs" color="red" ml="xs">Suspect</Badge>}
                  </Text>
                )}
              </Card>
            ))}
            
            {technicalContext.relatedJiraIssues?.map((issue, idx) => (
              <Card key={idx} padding="sm" withBorder>
                <Group justify="space-between">
                  <Text size="sm" fw={500}>{issue.key}</Text>
                  <Badge color="blue" variant="light">{issue.status}</Badge>
                </Group>
                <Text size="sm" c="dimmed" mt={4}>{issue.title}</Text>
              </Card>
            ))}
          </Stack>
        </Card>
      )}

      {/* REPRODUCIBILITY */}
      <Card padding="md" withBorder>
        <Group justify="space-between" mb="md">
          <Group gap="xs">
            <IconChecklist size={18} />
            <Text size="xs" tt="uppercase" fw={600} c="dimmed">Reproducibility</Text>
          </Group>
          <Badge
            size="lg"
            color={technicalContext.reproducibilityScore >= 80 ? 'teal' : technicalContext.reproducibilityScore >= 50 ? 'yellow' : 'red'}
          >
            {technicalContext.reproducibilityScore}% Reproducible
          </Badge>
        </Group>
        
        {technicalContext.reproductionSteps && technicalContext.reproductionSteps.length > 0 && (
          <>
            <Text size="sm" fw={500} mb="sm">Reproduction Steps:</Text>
            <Timeline bulletSize={20} lineWidth={2}>
              {technicalContext.reproductionSteps.map((step, idx) => (
                <Timeline.Item key={idx} bullet={<Text size="xs">{idx + 1}</Text>} color="teal">
                  <Text size="sm">{step}</Text>
                </Timeline.Item>
              ))}
            </Timeline>
            
            <CopyButton value={technicalContext.reproductionSteps.join('\n')}>
              {({ copied, copy }) => (
                <Button
                  variant="light"
                  fullWidth
                  mt="md"
                  onClick={copy}
                  leftSection={copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
                >
                  {copied ? 'Copied!' : 'Copy Repro Steps'}
                </Button>
              )}
            </CopyButton>
          </>
        )}
      </Card>

      {/* ENVIRONMENT INFO */}
      <Card padding="md" withBorder>
        <Group mb="md">
          <IconSettings size={18} />
          <Text size="xs" tt="uppercase" fw={600} c="dimmed">Environment</Text>
        </Group>
        
        <Stack gap="sm">
          <Group justify="space-between">
            <Text size="sm" c="dimmed">App Version</Text>
            <Code>{technicalContext.environmentInfo.appVersion}</Code>
          </Group>
          
          {technicalContext.environmentInfo.buildNumber && (
            <Group justify="space-between">
              <Text size="sm" c="dimmed">Build</Text>
              <Code>{technicalContext.environmentInfo.buildNumber}</Code>
            </Group>
          )}
          
          {technicalContext.environmentInfo.deployedAt && (
            <Group justify="space-between">
              <Text size="sm" c="dimmed">Deployed</Text>
              <Text size="sm">{new Date(technicalContext.environmentInfo.deployedAt).toLocaleString()}</Text>
            </Group>
          )}
          
          <Divider />
          
          <Text size="xs" tt="uppercase" fw={600} c="dimmed">Feature Flags</Text>
          <Stack gap="xs">
            {Object.entries(technicalContext.environmentInfo.featureFlags).map(([flag, enabled]) => (
              <Group key={flag} justify="space-between">
                <Code style={{ fontSize: '11px' }}>{flag}</Code>
                <Badge size="sm" color={enabled ? 'teal' : 'gray'}>
                  {enabled ? 'ON' : 'OFF'}
                </Badge>
              </Group>
            ))}
          </Stack>
        </Stack>
      </Card>

      {/* QUICK ACTIONS */}
      <Card padding="md" withBorder style={{ position: 'sticky', bottom: 0, backgroundColor: 'var(--mantine-color-body)' }}>
        <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">Engineering Actions</Text>
        
        <Stack gap="xs">
          <Button variant="filled" color="orange" leftSection={<IconBug size={16} />}>
            Create Jira Ticket
          </Button>
          <Button variant="light" leftSection={<IconGitBranch size={16} />}>
            Link to PR
          </Button>
          <Button variant="light" leftSection={<IconExternalLink size={16} />}>
            View Error Group
          </Button>
        </Stack>
      </Card>
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

function isRecent(dateStr: string): boolean {
  const date = new Date(dateStr);
  const now = new Date();
  const diffDays = (now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24);
  return diffDays < 7; // Last 7 days
}
