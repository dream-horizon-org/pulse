import { useParams, useNavigate } from 'react-router-dom';
import { Button, Container, Stack, Text, Title } from '@mantine/core';
import { IconArrowLeft } from '@tabler/icons-react';

/**
 * Session Replay Detail Page
 * 
 * Shows the detailed view of a single session replay including:
 * - Replay player
 * - Session metadata and summary stats
 * - Event timeline (flame chart)
 * - Console logs, network requests, performance metrics
 * - Critical interaction markers
 * 
 * Based on wireframe: pulse-docs/session-replay/03-detail-page/SESSION_DETAIL_WIREFRAME.md
 */
export const SessionReplayDetail: React.FC = () => {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();

  const handleBack = () => {
    navigate('/session-replay');
  };

  return (
    <Container size="xl" py="md">
      <Stack gap="md">
        <Button
          variant="subtle"
          leftSection={<IconArrowLeft size={16} />}
          onClick={handleBack}
        >
          Back to Session Replay List
        </Button>

        <Title order={2}>Session Replay Detail</Title>
        <Text size="sm" c="dimmed">
          Session ID: {sessionId}
        </Text>

        <Text c="dimmed" mt="xl" ta="center">
          🚧 Implementation in progress...
        </Text>
        <Text size="sm" c="dimmed" ta="center">
          This page will show the full session replay player, timeline, and context tabs.
        </Text>
      </Stack>
    </Container>
  );
};
