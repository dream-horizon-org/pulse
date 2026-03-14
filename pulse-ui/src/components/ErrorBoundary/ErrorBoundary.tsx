import { Component, ErrorInfo, ReactNode } from "react";
import { Box, Button, Text, Stack } from "@mantine/core";

type Props = {
  children: ReactNode;
};

type State = {
  hasError: boolean;
};

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("ErrorBoundary caught:", error, info.componentStack);
  }

  handleRetry = () => {
    this.setState({ hasError: false });
  };

  render() {
    if (this.state.hasError) {
      return (
        <Box
          style={{
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            minHeight: "60vh",
          }}
        >
          <Stack align="center" gap="md">
            <Text size="lg" fw={600} c="dark.4">
              Something went wrong
            </Text>
            <Text size="sm" c="dimmed">
              The page failed to load. This might be a temporary issue.
            </Text>
            <Button variant="light" onClick={this.handleRetry}>
              Try Again
            </Button>
          </Stack>
        </Box>
      );
    }

    return this.props.children;
  }
}
