import { Text, Loader } from "@mantine/core";
import { SESSION_LIST_LABELS } from "../constants/sessionList.constants";
import classes from "../SessionReplaySessions.module.css";

export function SessionListLoadingState() {
  return (
    <div className={classes.container}>
      <div className={classes.loadingContainer}>
        <Loader color="teal" size="lg" />
        <Text size="sm" c="dimmed">
          {SESSION_LIST_LABELS.loading}
        </Text>
      </div>
    </div>
  );
}
