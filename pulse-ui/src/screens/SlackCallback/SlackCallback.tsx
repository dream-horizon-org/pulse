/**
 * Slack OAuth Callback Handler
 * Handles redirect from Slack after user approves/denies OAuth
 * Exchanges code for token via backend and redirects to notification settings
 */

import { useEffect, useRef, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { Box, Text, Loader } from "@mantine/core";
import { IconCircleCheckFilled, IconAlertCircle } from "@tabler/icons-react";
import { useSlackCallback } from "../../hooks/useSlackCallback";
import { queryClient } from "../../clients/react-query";
import { API_ROUTES } from "../../constants";
import classes from "./SlackCallback.module.css";

const REDIRECT_DELAY_MS = 2500;

export function SlackCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { exchangeCode, isLoading, error } = useSlackCallback();
  const [result, setResult] = useState<{
    success: boolean;
    workspaceName?: string;
    message: string;
  } | null>(null);
  const hasRun = useRef(false);
  const redirectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const code = searchParams.get("code");
  const state = searchParams.get("state");
  const oauthError = searchParams.get("error");

  useEffect(() => {
    if (hasRun.current) return;
    hasRun.current = true;

    const run = async () => {
      const response = await exchangeCode({
        code: code || "",
        state: state || "",
        error: oauthError || undefined,
      });

      if (response) {
        setResult({
          success: response.success,
          workspaceName: response.workspaceName ?? undefined,
          message: response.message,
        });

        if (response.success && state) {
          // Invalidate channels so NotificationChannels refetches on redirect
          queryClient.invalidateQueries({
            queryKey: [API_ROUTES.SLACK_CHANNELS.key, state],
          });
          queryClient.invalidateQueries({
            queryKey: [API_ROUTES.GET_ALERT_NOTIFICATION_CHANNELS.key],
          });

          // Auto-redirect after delay
          redirectTimerRef.current = setTimeout(() => {
            navigate(`/projects/${state}/settings/notifications?from_slack=1`, {
              replace: true,
            });
          }, REDIRECT_DELAY_MS);
        }
      } else if (oauthError) {
        setResult({
          success: false,
          message:
            oauthError === "access_denied"
              ? "You cancelled the Slack authorization."
              : `Slack authorization failed: ${oauthError}`,
        });
      } else if (error) {
        setResult({
          success: false,
          message: error.message,
        });
      } else if (!code || !state) {
        setResult({
          success: false,
          message: "Invalid callback: missing code or project ID.",
        });
      }
    };

    run();

    return () => {
      if (redirectTimerRef.current) {
        clearTimeout(redirectTimerRef.current);
      }
    };
  }, [code, state, oauthError, exchangeCode, error, navigate]);

  // Loading state
  if (isLoading || (!result && !oauthError && !error && (code || oauthError))) {
    return (
      <Box className={classes.container}>
        <Box className={classes.card}>
          <Box
            className={`${classes.iconWrapper} ${classes.iconWrapperLoading}`}
          >
            <Loader size={36} />
          </Box>
          <Text className={classes.title}>Completing Slack connection...</Text>
          <Text className={classes.subtitle}>
            Please wait while we set up your workspace.
          </Text>
        </Box>
      </Box>
    );
  }

  // Success state
  if (result?.success) {
    return (
      <Box className={classes.container}>
        <Box className={classes.card}>
          <Box
            className={`${classes.iconWrapper} ${classes.iconWrapperSuccess}`}
          >
            <IconCircleCheckFilled size={40} />
          </Box>
          <Text className={classes.title}>Slack connected!</Text>
          <Text className={classes.subtitle}>{result.message}</Text>
          {result.workspaceName && (
            <Box className={classes.workspaceInfo}>
              <Text className={classes.workspaceName}>
                Workspace: {result.workspaceName}
              </Text>
            </Box>
          )}
          <Text className={classes.redirectHint}>
            Redirecting to notification settings...
          </Text>
        </Box>
      </Box>
    );
  }

  // Error state
  const errorMessage = result?.message || "An unexpected error occurred.";
  return (
    <Box className={classes.container}>
      <Box className={classes.card}>
        <Box className={`${classes.iconWrapper} ${classes.iconWrapperError}`}>
          <IconAlertCircle size={40} />
        </Box>
        <Text className={classes.title}>Connection failed</Text>
        <Text className={classes.subtitle}>{errorMessage}</Text>
        <Text
          className={classes.redirectHint}
          style={{ cursor: "pointer", textDecoration: "underline" }}
          onClick={() =>
            state
              ? navigate(`/projects/${state}/settings/notifications`)
              : navigate("/")
          }
        >
          {state ? "Return to notification settings" : "Return to dashboard"}
        </Text>
      </Box>
    </Box>
  );
}
