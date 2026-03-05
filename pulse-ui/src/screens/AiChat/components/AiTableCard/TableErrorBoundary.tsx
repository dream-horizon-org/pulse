import { Component, ReactNode } from "react";
import { Box, Text } from "@mantine/core";
import { IconAlertTriangle } from "@tabler/icons-react";
import classes from "./TableErrorBoundary.module.css";

interface Props {
  children: ReactNode;
  tableConfig?: unknown;
}

interface State {
  hasError: boolean;
}

export class TableErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error) {
    console.error("[TableErrorBoundary] Render failed:", error, this.props.tableConfig);
  }

  render() {
    if (this.state.hasError) {
      return (
        <Box className={classes.errorFallback}>
          <IconAlertTriangle size={16} color="var(--mantine-color-gray-5)" />
          <Text size="sm" c="dimmed">
            Table could not be rendered
          </Text>
        </Box>
      );
    }

    return this.props.children;
  }
}
