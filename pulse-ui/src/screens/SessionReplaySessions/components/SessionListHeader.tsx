import { Button, Group, Stack } from "@mantine/core";
import { useNavigate } from "react-router-dom";
import { IconArrowLeft } from "@tabler/icons-react";
import { SESSION_LIST_LABELS } from "../constants/sessionList.constants";
import classes from "../SessionReplaySessions.module.css";

export interface SessionListHeaderProps {
  /** Override subtitle (e.g. for empty state) */
  subtitle?: string;
}

export function SessionListHeader({ subtitle }: SessionListHeaderProps = {}) {
  const navigate = useNavigate();
  const sub = subtitle ?? SESSION_LIST_LABELS.pageSubtitle;
  return (
    <Stack gap="md" mb="lg">
      <Group justify="space-between" align="center">
        <Button
          variant="subtle"
          leftSection={<IconArrowLeft size={16} />}
          onClick={() => navigate("/session-replay/insights")}
        >
          {SESSION_LIST_LABELS.backToInsights}
        </Button>
      </Group>
      <div>
        <h1 className={classes.title}>{SESSION_LIST_LABELS.pageTitle}</h1>
        <p className={classes.subtitle}>{sub}</p>
      </div>
    </Stack>
  );
}
