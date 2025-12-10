import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Box,
  Text,
  Badge,
  Group,
  Loader,
  Tooltip,
  useMantineTheme,
} from "@mantine/core";
import {
  IconArrowLeft,
  IconEdit,
  IconBellX,
  IconBellRinging,
  IconCircleCheckFilled,
  IconSquareRoundedX,
} from "@tabler/icons-react";
import { AlertDetailProps } from "./AlertDetail.interface";
import classes from "./AlertDetail.module.css";
import { COMMON_CONSTANTS, ROUTES } from "../../constants";
import { useState, useEffect } from "react";
import { useGetAlertEvaluationHistory } from "../../hooks/useGetAlertEvaluationHistory";
import { useGetAlertDetails } from "../../hooks/useGetAlertDetails";
import { useSnoozeAlert } from "../../hooks/useSnoozeAlert";
import { useResumeAlert } from "../../hooks/useResumeAlert";
import { showNotification } from "../../helpers/showNotification";

// Map scope IDs to display labels
const SCOPE_LABELS: Record<string, string> = {
  interaction: "Interactions",
  network_api: "Network APIs",
  app_vitals: "App Vitals",
  screen: "Screen",
};

const getScopeLabel = (scopeId: string): string => {
  return SCOPE_LABELS[scopeId] || scopeId;
};

export function AlertDetail(_props: AlertDetailProps) {
  const navigate = useNavigate();
  const theme = useMantineTheme();
  const { alertId } = useParams<{ alertId: string }>();
  const [showSnoozeLoader, setShowSnoozeLoader] = useState(false);

  const {
    data: alertData,
    isLoading: isAlertLoading,
    refetch: refetchAlert,
  } = useGetAlertDetails({
    queryParams: {
      alert_id: alertId || null,
    },
  });

  const {
    data: evaluationHistoryData,
    isLoading: isHistoryLoading,
    refetch: refetchHistory,
  } = useGetAlertEvaluationHistory({
    alertId: alertId || "",
  });

  const snoozeAlertMutation = useSnoozeAlert({
    onSettled: (data, error) => {
      setShowSnoozeLoader(false);
      if (error || data?.error) {
        showNotification(
          COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
          data?.error?.message || "Failed to snooze alert",
          <IconSquareRoundedX />,
          theme.colors.red[6],
        );
        return;
      }
      showNotification(
        COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE,
        "Alert snoozed successfully",
        <IconCircleCheckFilled />,
        theme.colors.teal[6],
      );
      refetchAlert();
    },
  });

  const resumeAlertMutation = useResumeAlert({
    onSettled: (data, error) => {
      setShowSnoozeLoader(false);
      if (error || data?.error) {
        showNotification(
          COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
          data?.error?.message || "Failed to resume alert",
          <IconSquareRoundedX />,
          theme.colors.red[6],
        );
        return;
      }
      showNotification(
        COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE,
        "Alert resumed successfully",
        <IconCircleCheckFilled />,
        theme.colors.teal[6],
      );
      refetchAlert();
    },
  });

  // Fetch evaluation history on mount
  useEffect(() => {
    if (alertId) {
      refetchHistory();
    }
  }, [alertId, refetchHistory]);

  const handleBack = () => {
    navigate(ROUTES.ALERTS.basePath);
  };

  const handleEdit = () => {
    navigate(`${ROUTES.ALERTS_FORM.basePath}/${alertId}`);
  };

  const handleSnoozeToggle = () => {
    if (!alertId) return;
    setShowSnoozeLoader(true);

    if (alertData?.data?.is_snoozed) {
      resumeAlertMutation.mutate(alertId);
    } else {
      // Snooze for 24 hours by default
      const now = Date.now();
      const snoozeUntil = now + 24 * 60 * 60 * 1000;
      snoozeAlertMutation.mutate({
        alertId,
        snoozeAlertRequest: {
          snoozed_from: now,
          snoozed_until: snoozeUntil,
        },
      });
    }
  };

  const alert = alertData?.data;
  const isFiring = alert?.current_state === "FIRING";

  if (isAlertLoading) {
    return (
      <div className={classes.pageContainer}>
        <Box
          style={{
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            height: "50vh",
          }}
        >
          <Loader size="lg" />
        </Box>
      </div>
    );
  }

  if (!alert) {
    return (
      <div className={classes.pageContainer}>
        <Button
          variant="subtle"
          leftSection={<IconArrowLeft size={16} />}
          onClick={handleBack}
          className={classes.backButton}
        >
          Back
        </Button>
        <Box
          style={{ textAlign: "center", padding: "40px", marginTop: "20px" }}
        >
          <Text size="lg" c="dimmed">
            Alert not found
          </Text>
        </Box>
      </div>
    );
  }

  return (
    <div className={classes.pageContainer}>
      {/* Header */}
      <div className={classes.headerContainer}>
        <Button
          variant="subtle"
          leftSection={<IconArrowLeft size={16} />}
          onClick={handleBack}
          className={classes.backButton}
        >
          Back
        </Button>
        <div className={classes.titleSection}>
          <div className={classes.titleRow}>
            <h1 className={classes.pageTitle}>{alert.name}</h1>
            <Badge
              size="lg"
              variant="light"
              color={
                alert.is_snoozed ? "gray" : isFiring ? "red" : "green"
              }
              className={classes.stateBadge}
            >
              {alert.is_snoozed ? "Snoozed" : isFiring ? "Firing" : "Normal"}
            </Badge>
          </div>
          {alert.description && (
            <Text size="sm" c="dimmed" className={classes.subtitle}>
              {alert.description}
            </Text>
          )}
        </div>
        <Group gap="sm">
          <Tooltip label="Edit alert">
            <Button
              variant="outline"
              onClick={handleEdit}
              className={classes.actionButton}
            >
              <IconEdit size={16} />
            </Button>
          </Tooltip>

          <Tooltip label={alert.is_snoozed ? "Resume alert" : "Snooze alert"}>
            <Button
              variant="outline"
              onClick={handleSnoozeToggle}
              className={classes.actionButton}
            >
              {showSnoozeLoader ? (
                <Loader size={16} type="bars" />
              ) : alert.is_snoozed ? (
                <IconBellRinging size={16} />
              ) : (
                <IconBellX size={16} />
              )}
            </Button>
          </Tooltip>
        </Group>
      </div>

      {/* Main Content Grid */}
      <div className={classes.contentGrid}>
        {/* Left Column */}
        <div className={classes.leftColumn}>
          {/* Alert Configuration */}
          <Box className={classes.detailsSection}>
            <Text className={classes.sectionTitle}>Alert Configuration</Text>
            <Box className={classes.detailsGrid}>
              <Box className={classes.detailCard}>
                <Text className={classes.detailLabel}>Scope</Text>
                <Text className={classes.detailValue}>{getScopeLabel(alert.scope)}</Text>
              </Box>
              <Box className={classes.detailCard}>
                <Text className={classes.detailLabel}>Severity</Text>
                <Badge
                  size="md"
                  variant="light"
                  color={alert.severity_id <= 3 ? "red" : "orange"}
                >
                  P{alert.severity_id}
                </Badge>
              </Box>
              <Box className={classes.detailCard}>
                <Text className={classes.detailLabel}>Eval Interval</Text>
                <Text className={classes.detailValue}>
                  {alert.evaluation_interval}s
                </Text>
              </Box>
              <Box className={classes.detailCard}>
                <Text className={classes.detailLabel}>Eval Period</Text>
                <Text className={classes.detailValue}>
                  {alert.evaluation_period}s
                </Text>
              </Box>
              <Box className={classes.detailCard}>
                <Text className={classes.detailLabel}>Active</Text>
                <Text className={classes.detailValue}>
                  {alert.is_active ? "Yes" : "No"}
                </Text>
              </Box>
              <Box className={classes.detailCard}>
                <Text className={classes.detailLabel}>Condition</Text>
                <Text className={classes.detailValue}>
                  {alert.condition_expression}
                </Text>
              </Box>
            </Box>
          </Box>

          {/* Alert Conditions */}
          <Box className={classes.detailsSection}>
            <Text className={classes.sectionTitle}>Alert Conditions</Text>
            {alert.alerts?.map((condition, index) => (
              <Box key={index} className={classes.conditionCard}>
                <Text className={classes.conditionAlias}>
                  Condition {condition.alias}
                </Text>
                <Box className={classes.detailsGrid}>
                  <Box className={classes.detailCard}>
                    <Text className={classes.detailLabel}>Metric</Text>
                    <Text className={classes.detailValue}>
                      {condition.metric}
                    </Text>
                  </Box>
                  <Box className={classes.detailCard}>
                    <Text className={classes.detailLabel}>Operator</Text>
                    <Text className={classes.detailValue}>
                      {condition.metric_operator}
                    </Text>
                  </Box>
                  {Object.entries(condition.threshold || {}).map(
                    ([key, value]) => (
                      <Box key={key} className={classes.detailCard}>
                        <Text className={classes.detailLabel}>
                          Threshold ({key})
                        </Text>
                        <Text className={classes.detailValue}>{value}</Text>
                      </Box>
                    ),
                  )}
                </Box>
              </Box>
            ))}
          </Box>

          {/* Metadata */}
          <Box className={classes.detailsSection}>
            <Text className={classes.sectionTitle}>Metadata</Text>
            <Box className={classes.detailsGrid}>
              <Box className={classes.detailCard}>
                <Text className={classes.detailLabel}>Created By</Text>
                <Text className={classes.detailValue}>{alert.created_by}</Text>
              </Box>
              {alert.updated_by && (
                <Box className={classes.detailCard}>
                  <Text className={classes.detailLabel}>Updated By</Text>
                  <Text className={classes.detailValue}>{alert.updated_by}</Text>
                </Box>
              )}
              <Box className={classes.detailCard}>
                <Text className={classes.detailLabel}>Created At</Text>
                <Text className={classes.detailValue}>
                  {new Date(alert.created_at).toLocaleString()}
                </Text>
              </Box>
              <Box className={classes.detailCard}>
                <Text className={classes.detailLabel}>Updated At</Text>
                <Text className={classes.detailValue}>
                  {new Date(alert.updated_at).toLocaleString()}
                </Text>
              </Box>
            </Box>
          </Box>
        </div>

        {/* Right Column - Evaluation History */}
        <div className={classes.rightColumn}>
          <Box className={classes.detailsSection}>
            <Text className={classes.sectionTitle}>Evaluation History</Text>
            <Box className={classes.historyContainer}>
              {isHistoryLoading ? (
                <div className={classes.historyLoader}>
                  <Loader size="md" />
                  <Text size="sm" c="dimmed">
                    Loading evaluation history...
                  </Text>
                </div>
              ) : evaluationHistoryData?.error ? (
                <div className={classes.historyError}>
                  <Text size="sm" c="red">
                    {evaluationHistoryData.error.message}
                  </Text>
                </div>
              ) : evaluationHistoryData?.data &&
                evaluationHistoryData.data.length > 0 ? (
                <div className={classes.historyList}>
                  {evaluationHistoryData.data.map((item, index) => (
                    <div key={index} className={classes.historyItem}>
                      <div className={classes.historyItemHeader}>
                        <Badge
                          size="sm"
                          variant="light"
                          color={
                            item.current_state === "FIRING" ? "red" : "green"
                          }
                        >
                          {item.current_state}
                        </Badge>
                        <Text size="xs" c="dimmed">
                          {new Date(item.evaluated_at).toLocaleString()}
                        </Text>
                      </div>
                      <div className={classes.historyDetails}>
                        {item.reading !== undefined && (
                          <Text size="sm" className={classes.historyValue}>
                            Reading: <strong>{item.reading}</strong>
                          </Text>
                        )}
                        {item.threshold !== undefined && (
                          <Text size="sm" className={classes.historyValue}>
                            Threshold: <strong>{item.threshold}</strong>
                          </Text>
                        )}
                        {item.metric && (
                          <Text size="xs" c="dimmed" mt={4}>
                            Metric: {item.metric}
                          </Text>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className={classes.historyEmpty}>
                  <Text size="sm" c="dimmed">
                    No evaluation history available
                  </Text>
                </div>
              )}
            </Box>
          </Box>
        </div>
      </div>
    </div>
  );
}

