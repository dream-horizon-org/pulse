import { Stack } from "@mantine/core";
import { SESSION_LIST_LABELS } from "../constants/sessionList.constants";
import classes from "../SessionReplaySessions.module.css";

export interface SessionListHeaderProps {
  /** Override subtitle (e.g. for empty state) */
  subtitle?: string;
}

export function SessionListHeader({ subtitle }: SessionListHeaderProps = {}) {
  const sub = subtitle ?? SESSION_LIST_LABELS.pageSubtitle;
  return (
    <Stack gap="md" mb="lg">
      <div>
        <h1 className={classes.title}>{SESSION_LIST_LABELS.pageTitle}</h1>
        <p className={classes.subtitle}>{sub}</p>
      </div>
    </Stack>
  );
}
