import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader, Text } from '@mantine/core';

/**
 * Session Replay Landing Page
 * 
 * Redirects to /session-replay/insights (the metrics page)
 * 
 * Route: /session-replay
 */
export function SessionReplay() {
  const navigate = useNavigate();

  useEffect(() => {
    navigate('/session-replay/insights', { replace: true });
  }, [navigate]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '400px', gap: '16px' }}>
      <Loader color="teal" size="lg" />
      <Text size="sm" c="dimmed">Redirecting to insights...</Text>
    </div>
  );
}
