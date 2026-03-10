import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Box,
  Container,
  Text,
  Loader,
  Alert,
  Button,
  Stack,
} from "@mantine/core";
import { IconCheck, IconAlertCircle } from "@tabler/icons-react";
import { useSlackCallback } from "../../hooks/useSlackCallback";
import { SlackOAuthResponseDto } from "../../hooks/useGetAlertNotificationChannels/useGetAlertNotificationChannels.interface";
import classes from "./SlackCallback.module.css";

export function SlackCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { completeCallback } = useSlackCallback();
  const [status, setStatus] = useState<"loading" | "success" | "error">(
    "loading",
  );
  const [message, setMessage] = useState("");
  const [workspaceName, setWorkspaceName] = useState<string | null>(null);

  useEffect(() => {
    const code = searchParams.get("code");
    const state = searchParams.get("state"); // projectId
    const error = searchParams.get("error");

    if (error) {
      setStatus("error");
      setMessage(`OAuth error: ${error}`);
      return;
    }

    if (!code || !state) {
      setStatus("error");
      setMessage("Missing authorization code or project ID. Please try again.");
      return;
    }

    let cancelled = false;

    completeCallback({ code, state })
      .then((data: SlackOAuthResponseDto | null) => {
        if (cancelled) return;

        if (data?.success) {
          setStatus("success");
          setWorkspaceName(data.workspaceName || null);
          setMessage(data.message || "Slack workspace connected successfully!");

          // Redirect after 2.5 seconds
          setTimeout(() => {
            navigate(
              `/projects/${state}/settings/notifications?slack_success=1`,
              { replace: true },
            );
          }, 2500);
        } else {
          setStatus("error");
          setMessage(data?.message || "Failed to connect Slack workspace");
        }
      })
      .catch((err: Error) => {
        if (cancelled) return;
        setStatus("error");
        setMessage(err.message || "Unknown error occurred");
      });

    return () => {
      cancelled = true;
    };
  }, [searchParams, completeCallback, navigate]);

  const projectId = searchParams.get("state");

  return (
    <Box className={classes.container}>
      <Container size="sm">
        <Stack align="center" gap="xl">
          {status === "loading" && (
            <>
              <Loader size="xl" color="teal" />
              <Text size="lg" fw={500}>
                Connecting to Slack...
              </Text>
              <Text size="sm" c="dimmed">
                Please wait while we complete the setup
              </Text>
            </>
          )}

          {status === "success" && (
            <>
              <Box className={classes.iconWrapper}>
                <IconCheck size={48} className={classes.successIcon} />
              </Box>
              <Alert
                icon={<IconCheck size={20} />}
                color="teal"
                title="Success!"
                w="100%"
              >
                <Text size="sm">
                  {workspaceName
                    ? `Successfully connected to ${workspaceName}!`
                    : "Slack workspace connected successfully!"}
                </Text>
                <Text size="xs" c="dimmed" mt="xs">
                  {message}
                </Text>
              </Alert>
              <Text size="sm" c="dimmed">
                Redirecting to notification settings...
              </Text>
            </>
          )}

          {status === "error" && (
            <>
              <Alert
                icon={<IconAlertCircle size={20} />}
                color="red"
                title="Connection Failed"
                w="100%"
              >
                <Text size="sm">{message}</Text>
              </Alert>
              <Button
                variant="light"
                onClick={() =>
                  navigate(`/projects/${projectId}/settings/notifications`, {
                    replace: true,
                  })
                }
              >
                Back to Settings
              </Button>
            </>
          )}
        </Stack>
      </Container>
    </Box>
  );
}
