import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { getProjectContext } from '../../helpers/projectContext';
import { ROUTES } from '../../constants';
import { Box, Loader, Text } from '@mantine/core';

export function LegacyRedirect() {
  const navigate = useNavigate();
  const location = useLocation();
  const projectContext = getProjectContext();

  useEffect(() => {
    if (projectContext) {
      const path = location.pathname;
      const newPath = `/projects/${projectContext.projectId}${path}`;
      console.log(`[LegacyRedirect] Redirecting ${path} -> ${newPath}`);
      navigate(newPath, { replace: true });
    } else {
      console.log('[LegacyRedirect] No project context, redirecting to project selection');
      navigate(ROUTES.PROJECT_SELECTION.basePath, { replace: true });
    }
  }, [projectContext, location, navigate]);

  return (
    <Box
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        gap: '1rem',
      }}
    >
      <Loader size="lg" />
      <Text size="lg" c="dimmed">Redirecting...</Text>
    </Box>
  );
}
