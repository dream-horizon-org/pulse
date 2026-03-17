import { Component, ReactNode } from "react";
import { Box, Text } from "@mantine/core";
import { IconAlertTriangle } from "@tabler/icons-react";
import { AI_CHAT_TEXTS } from "../../AiChat.constants";
import classes from "./ChartErrorBoundary.module.css";

interface Props {
  children: ReactNode;
  chartConfig?: unknown;
}

interface State {
  hasError: boolean;
}

export class ChartErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error) {
    console.error(
      "[ChartErrorBoundary] Render failed:",
      error,
      this.props.chartConfig,
    );
  }

  render() {
    if (this.state.hasError) {
      return (
        <Box className={classes.errorFallback}>
          <IconAlertTriangle size={16} color="var(--mantine-color-gray-5)" />
          <Text size="sm" c="dimmed">
            {AI_CHAT_TEXTS.CHART_RENDER_ERROR}
          </Text>
        </Box>
      );
    }

    return this.props.children;
  }
}
