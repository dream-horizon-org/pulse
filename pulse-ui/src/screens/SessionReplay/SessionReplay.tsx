import { useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { Loader, Text } from "@mantine/core";

export function SessionReplay() {
  const navigate = useNavigate();
  const { pathname } = useLocation();

  useEffect(() => {
    const projectMatch = pathname.match(/^\/projects\/([^/]+)/);
    const base = projectMatch
      ? `/projects/${projectMatch[1]}/session-replay`
      : "/session-replay";
    navigate(`${base}/sessions`, { replace: true });
  }, [navigate, pathname]);

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        minHeight: "400px",
        gap: "16px",
      }}
    >
      <Loader color="teal" size="lg" />
      <Text size="sm" c="dimmed">
        Redirecting to insights...
      </Text>
    </div>
  );
}
