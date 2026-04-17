import { Outlet } from 'react-router-dom';
import { SessionReplayFilterProvider } from '../../contexts/SessionReplayFilterContext';

/**
 * Wrapper component for Session Replay routes
 * Provides filter context to all Session Replay pages
 */
export function SessionReplayWrapper() {
  return (
    <SessionReplayFilterProvider>
      <Outlet />
    </SessionReplayFilterProvider>
  );
}
