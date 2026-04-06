import { Center, Loader } from "@mantine/core";
import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { ROUTES } from "../../constants";
import { useProjectContext } from "../../contexts";
import { useSessionReplayFromActiveConfig } from "../../hooks/useSessionReplayFromActiveConfig";

type SessionReplayRouteGuardProps = {
  children: React.ReactNode;
};


export function SessionReplayRouteGuard({
  children,
}: SessionReplayRouteGuardProps) {
  const navigate = useNavigate();
  const { projectId, isInitializing } = useProjectContext();
  const { isSessionReplayEnabled, isLoading } =
    useSessionReplayFromActiveConfig({
      enabled: Boolean(projectId),
      projectId,
    });

  useEffect(() => {
    if (isInitializing || !projectId || isLoading) return;
    if (!isSessionReplayEnabled) {
      const home = ROUTES.PROJECT_DASHBOARD.basePath.replace(
        ":projectId",
        projectId,
      );
      navigate(home, { replace: true });
    }
  }, [isInitializing, isLoading, isSessionReplayEnabled, navigate, projectId]);

  if (isInitializing || !projectId) {
    return (
      <Center style={{ minHeight: 240 }}>
        <Loader color="teal" />
      </Center>
    );
  }

  if (isLoading) {
    return (
      <Center style={{ minHeight: 240 }}>
        <Loader color="teal" />
      </Center>
    );
  }

  if (!isSessionReplayEnabled) {
    return null;
  }

  return <>{children}</>;
}
