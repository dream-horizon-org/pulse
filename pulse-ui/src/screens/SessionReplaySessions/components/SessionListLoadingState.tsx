import { Text, Loader } from "@mantine/core";
import { SESSION_LIST_LABELS } from "../constants/sessionList.constants";
import classes from "../SessionReplaySessions.module.css";

export interface SessionListLoadingStateProps {
  /** When true, render only the inner loading block (e.g. below SessionsTableToolbar). */
  embedded?: boolean;
}

export function SessionListLoadingState({
  embedded = false,
}: SessionListLoadingStateProps = {}) {
  const inner = (
    <div className={classes.loadingContainer}>
      <Loader color="teal" size="lg" />
      <Text size="sm" c="dimmed">
        {SESSION_LIST_LABELS.loading}
      </Text>
    </div>
  );
  if (embedded) {
    return inner;
  }
  return <div className={classes.container}>{inner}</div>;
}
