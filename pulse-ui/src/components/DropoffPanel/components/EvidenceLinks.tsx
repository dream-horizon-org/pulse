import { Anchor, Box, Loader, Stack, Text } from "@mantine/core";
import { IconExternalLink } from "@tabler/icons-react";
import { useFunnelDropoffEvidence } from "../../../hooks/useFunnelDropoffEvidence";

interface EvidenceLinksProps {
  funnelId: string;
  stepIndex: number;
  sessionIds: string[];
  runTime?: string;
  /** When false the query stays disabled — avoids firing until the row expands. */
  enabled: boolean;
}

/**
 * Renders per-session links (replay + trace) for the "View examples" drill-in.
 * Uses the same session-details / trace-details routes as the rest of the app.
 */
export function EvidenceLinks({
  funnelId,
  stepIndex,
  sessionIds,
  runTime,
  enabled,
}: EvidenceLinksProps) {
  const { data: response, isLoading, isError } = useFunnelDropoffEvidence(
    enabled ? funnelId : undefined,
    enabled ? stepIndex : undefined,
    enabled ? sessionIds : undefined,
    runTime
  );
  const data = response?.data ?? null;

  if (!enabled) {
    return null;
  }

  if (isLoading) {
    return (
      <Box style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <Loader size="xs" />
        <Text size="xs" c="dimmed">
          Loading examples…
        </Text>
      </Box>
    );
  }

  if (isError) {
    return (
      <Text size="xs" c="red">
        Failed to load evidence.
      </Text>
    );
  }

  const examples = data?.examples ?? [];
  if (examples.length === 0) {
    return (
      <Text size="xs" c="dimmed">
        No evidence rows available for these sessions.
      </Text>
    );
  }

  return (
    <Stack gap={6}>
      {examples.map((ex) => {
        const sessionHref = `/sessions/${encodeURIComponent(ex.sessionId)}`;
        const traceHref = ex.traceId
          ? `/traces/${encodeURIComponent(ex.traceId)}`
          : null;
        return (
          <Box
            key={ex.sessionId}
            style={{
              borderLeft: "2px solid var(--mantine-color-blue-5)",
              paddingLeft: "var(--mantine-spacing-sm)",
            }}
          >
            <Text size="xs" fw={500}>
              {ex.screen || "(unknown screen)"} · {ex.appVersion || "?"} ·{" "}
              {ex.platform || "?"}
            </Text>
            <Text size="xs" c="dimmed">
              {ex.lastReachedAt}
            </Text>
            <Box style={{ display: "flex", gap: 12, marginTop: 2 }}>
              <Anchor href={sessionHref} target="_blank" size="xs">
                Replay <IconExternalLink size={10} />
              </Anchor>
              {traceHref && (
                <Anchor href={traceHref} target="_blank" size="xs">
                  Trace <IconExternalLink size={10} />
                </Anchor>
              )}
            </Box>
          </Box>
        );
      })}
    </Stack>
  );
}
