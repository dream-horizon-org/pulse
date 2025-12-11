import { useNavigate, useParams } from "react-router-dom";
import { Button, Badge, Loader, Tooltip, useMantineTheme, Divider } from "@mantine/core";
import {
  IconArrowLeft, IconEdit, IconBellX, IconBellRinging, IconCircleCheckFilled,
  IconSquareRoundedX, IconClock, IconUser, IconCalendar, IconActivity,
  IconBell, IconBellOff, IconAlertTriangle,
} from "@tabler/icons-react";
import { AlertDetailProps } from "./AlertDetail.interface";
import classes from "./AlertDetail.module.css";
import { COMMON_CONSTANTS, ROUTES } from "../../constants";
import { useState, useEffect } from "react";
import { useGetAlertEvaluationHistory } from "../../hooks/useGetAlertEvaluationHistory";
import { useGetAlertDetails } from "../../hooks/useGetAlertDetails";
import { useSnoozeAlert } from "../../hooks/useSnoozeAlert";
import { useResumeAlert } from "../../hooks/useResumeAlert";
import { useGetAlertScopes } from "../../hooks/useGetAlertScopes";
import { useGetAlertSeverities } from "../../hooks/useGetAlertSeverities";
import { showNotification } from "../../helpers/showNotification";

const METRIC_LABELS: Record<string, string> = {
  APDEX: "APDEX Score", CRASH_RATE: "Crash Rate", ANR_RATE: "ANR Rate",
  DURATION_P99: "P99 Latency", DURATION_P95: "P95 Latency", DURATION_P50: "P50 Latency",
  ERROR_RATE: "Error Rate", INTERACTION_ERROR_COUNT: "Error Count",
  SCREEN_LOAD_TIME_P99: "Load Time P99", SCREEN_LOAD_TIME_P95: "Load Time P95",
  NET_5XX_RATE: "5XX Rate", NET_4XX_RATE: "4XX Rate",
};

const OPERATOR_SYMBOLS: Record<string, string> = {
  GREATER_THAN: ">", LESS_THAN: "<", GREATER_THAN_OR_EQUAL: "≥", LESS_THAN_OR_EQUAL: "≤", EQUAL: "=",
};

const formatDuration = (seconds: number): string => {
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
};

const formatThresholdValue = (value: number, metric: string): string => {
  if (metric.includes("RATE") || metric === "APDEX") return `${(value * 100).toFixed(1)}%`;
  if (metric.includes("DURATION") || metric.includes("TIME")) return value >= 1000 ? `${(value / 1000).toFixed(1)}s` : `${value}ms`;
  return value.toLocaleString();
};

const formatDate = (date: string | Date) => new Date(date).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
const formatTime = (date: string | Date) => new Date(date).toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit" });
const formatDateTime = (date: string | Date) => `${formatDate(date)} at ${formatTime(date)}`;

// Parse reading JSON string to get per-scope readings
type ScopeReading = { reading: number; useCaseId: string; totalInteractionCount: number };
const parseReadings = (readingStr: string): ScopeReading[] => {
  try {
    return JSON.parse(readingStr);
  } catch {
    return [];
  }
};

export function AlertDetail(_props: AlertDetailProps) {
  const navigate = useNavigate();
  const theme = useMantineTheme();
  const { alertId } = useParams<{ alertId: string }>();
  const [showSnoozeLoader, setShowSnoozeLoader] = useState(false);

  const { data: alertData, isLoading: isAlertLoading, refetch: refetchAlert } = useGetAlertDetails({ queryParams: { alert_id: alertId || null } });
  const { data: evaluationHistoryData, isLoading: isHistoryLoading, refetch: refetchHistory } = useGetAlertEvaluationHistory({ alertId: alertId || "" });
  const { data: scopesData } = useGetAlertScopes();
  const { data: severitiesData } = useGetAlertSeverities();

  // Build lookup maps
  const scopeLabels: Record<string, string> = {};
  scopesData?.data?.scopes?.forEach((s: { id: string; label: string }) => { scopeLabels[s.id] = s.label; });
  
  const severityConfig: Record<number, { label: string; color: string }> = {};
  const colors = ["#ef4444", "#f59e0b", "#3b82f6"];
  const labels = ["P1 Critical", "P2 Warning", "P3 Info"];
  const severities = severitiesData?.data && Array.isArray(severitiesData.data) ? severitiesData.data : [];
  severities.forEach((s: { severity_id: number; name: number }) => {
    severityConfig[s.severity_id] = { label: labels[s.name - 1] || `P${s.name}`, color: colors[s.name - 1] || "#6b7280" };
  });

  const snoozeAlertMutation = useSnoozeAlert({
    onSettled: (data, error) => {
      setShowSnoozeLoader(false);
      if (error || data?.error) {
        showNotification(COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE, data?.error?.message || "Failed to snooze", <IconSquareRoundedX />, theme.colors.red[6]);
        return;
      }
      showNotification(COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE, "Alert snoozed", <IconCircleCheckFilled />, theme.colors.teal[6]);
      refetchAlert();
    },
  });

  const resumeAlertMutation = useResumeAlert({
    onSettled: (data, error) => {
      setShowSnoozeLoader(false);
      if (error || data?.error) {
        showNotification(COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE, data?.error?.message || "Failed to resume", <IconSquareRoundedX />, theme.colors.red[6]);
        return;
      }
      showNotification(COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE, "Alert resumed", <IconCircleCheckFilled />, theme.colors.teal[6]);
      refetchAlert();
    },
  });

  useEffect(() => { if (alertId) refetchHistory(); }, [alertId, refetchHistory]);

  const handleBack = () => navigate(ROUTES.ALERTS.basePath);
  const handleEdit = () => navigate(`${ROUTES.ALERTS_FORM.basePath}/${alertId}`);
  const handleSnoozeToggle = () => {
    if (!alertId) return;
    setShowSnoozeLoader(true);
    if (alertData?.data?.is_snoozed) {
      resumeAlertMutation.mutate(alertId);
    } else {
      const now = Date.now();
      snoozeAlertMutation.mutate({ alertId, snoozeAlertRequest: { snoozeFrom: now, snoozeUntil: now + 24 * 60 * 60 * 1000 } });
    }
  };

  const alert = alertData?.data;
  const isFiring = alert?.current_state === "FIRING";
  const severity = severityConfig[alert?.severity_id || 1] || { label: "Unknown", color: "#6b7280" };

  if (isAlertLoading) {
    return <div className={classes.pageContainer}><div className={classes.loaderCenter}><Loader size="lg" color="teal" /></div></div>;
  }

  if (!alert) {
    return (
      <div className={classes.pageContainer}>
        <button className={classes.backLink} onClick={handleBack}><IconArrowLeft size={16} /> Back to Alerts</button>
        <div className={classes.emptyState}><IconAlertTriangle size={48} stroke={1.5} /><span>Alert not found</span></div>
      </div>
    );
  }

  const StatusIcon = alert.is_snoozed ? IconBellOff : isFiring ? IconBellRinging : IconBell;
  const statusColor = alert.is_snoozed ? "#94a3b8" : isFiring ? "#ef4444" : "#10b981";
  const statusLabel = alert.is_snoozed ? "Snoozed" : isFiring ? "Firing" : "Normal";

  // Format snooze end time
  const formatSnoozeUntil = (timestamp: number | null) => {
    if (!timestamp) return "";
    const date = new Date(timestamp);
    return date.toLocaleString("en-US", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
  };

  return (
    <div className={classes.pageContainer}>
      {/* Back Link */}
      <button className={classes.backLink} onClick={handleBack}>
        <IconArrowLeft size={16} /> Back to Alerts
      </button>

      {/* Snooze Banner - shown when alert is snoozed */}
      {alert.is_snoozed && (
        <div className={classes.snoozeBanner}>
          <div className={classes.snoozeBannerContent}>
            <IconBellOff size={20} />
            <div className={classes.snoozeBannerText}>
              <span className={classes.snoozeBannerTitle}>Alert is Snoozed</span>
              {alert.snoozed_until && (
                <span className={classes.snoozeBannerUntil}>Until {formatSnoozeUntil(alert.snoozed_until)}</span>
              )}
            </div>
          </div>
          <Button 
            variant="white" 
            size="xs" 
            onClick={handleSnoozeToggle}
            loading={showSnoozeLoader}
            leftSection={<IconBellRinging size={14} />}
          >
            Resume Now
          </Button>
        </div>
      )}

      {/* Hero Header */}
      <div className={classes.heroHeader} style={{ "--status-color": statusColor } as React.CSSProperties}>
        <div className={classes.heroLeft}>
          <div className={classes.statusIcon} style={{ background: `${statusColor}15`, color: statusColor }}>
            <StatusIcon size={28} stroke={1.8} />
          </div>
          <div className={classes.heroInfo}>
            <div className={classes.heroBadges}>
              <Badge size="sm" variant="light" color="gray">{scopeLabels[alert.scope] || alert.scope}</Badge>
              <Badge size="sm" variant="light" style={{ backgroundColor: `${severity.color}15`, color: severity.color }}>{severity.label}</Badge>
              <Badge size="sm" variant="filled" style={{ backgroundColor: statusColor }}>{statusLabel}</Badge>
            </div>
            <h1 className={classes.heroTitle}>{alert.name}</h1>
            {alert.description && <p className={classes.heroDescription}>{alert.description}</p>}
          </div>
        </div>
        <div className={classes.heroActions}>
          <Tooltip label="Edit Alert">
            <Button variant="light" color="teal" onClick={handleEdit}><IconEdit size={18} /></Button>
          </Tooltip>
          {!alert.is_snoozed && (
            <Tooltip label="Snooze for 24h">
              <Button variant="light" color="orange" onClick={handleSnoozeToggle} loading={showSnoozeLoader}>
                <IconBellX size={18} />
              </Button>
            </Tooltip>
          )}
        </div>
      </div>

      {/* Main Content */}
      <div className={classes.contentGrid}>
        {/* Left Column */}
        <div className={classes.mainContent}>
          {/* Conditions Section */}
          <section className={classes.section}>
            <h2 className={classes.sectionTitle}><IconActivity size={18} /> Alert Conditions</h2>
            <p className={classes.sectionSubtitle}>Expression: <code className={classes.expressionCode}>{alert.condition_expression}</code></p>
            <div className={classes.conditionsGrid}>
              {alert.alerts?.map((condition, idx) => (
                <div key={idx} className={classes.conditionCard}>
                  <div className={classes.conditionHeader}>
                    <span className={classes.conditionAlias}>{condition.alias}</span>
                    <span className={classes.conditionMetric}>{METRIC_LABELS[condition.metric] || condition.metric}</span>
                    <span className={classes.conditionOperator}>{OPERATOR_SYMBOLS[condition.metric_operator] || condition.metric_operator}</span>
                  </div>
                  <Divider my="xs" color="rgba(14,201,194,0.1)" />
                  <div className={classes.thresholdsList}>
                    {Object.entries(condition.threshold || {}).map(([name, value]) => (
                      <div key={name} className={classes.thresholdItem}>
                        <span className={classes.thresholdName}>{name}</span>
                        <span className={classes.thresholdValue}>{formatThresholdValue(value as number, condition.metric)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </section>

          {/* Configuration Section */}
          <section className={classes.section}>
            <h2 className={classes.sectionTitle}><IconClock size={18} /> Evaluation Settings</h2>
            <div className={classes.configGrid}>
              <div className={classes.configItem}>
                <span className={classes.configLabel}>Evaluation Period</span>
                <span className={classes.configValue}>{formatDuration(alert.evaluation_period)}</span>
              </div>
              <div className={classes.configItem}>
                <span className={classes.configLabel}>Check Interval</span>
                <span className={classes.configValue}>{formatDuration(alert.evaluation_interval)}</span>
              </div>
              <div className={classes.configItem}>
                <span className={classes.configLabel}>Notification Channel</span>
                <span className={classes.configValue}>Channel #{alert.notification_channel_id}</span>
              </div>
              <div className={classes.configItem}>
                <span className={classes.configLabel}>Status</span>
                <span className={classes.configValue}>{alert.is_active ? "Active" : "Inactive"}</span>
              </div>
            </div>
          </section>

          {/* Metadata Section */}
          <section className={classes.section}>
            <h2 className={classes.sectionTitle}><IconUser size={18} /> Metadata</h2>
            <div className={classes.metadataGrid}>
              <div className={classes.metadataItem}>
                <IconUser size={14} />
                <span>Created by <strong>{alert.created_by}</strong></span>
              </div>
              <div className={classes.metadataItem}>
                <IconCalendar size={14} />
                <span>Created {formatDateTime(alert.created_at)}</span>
              </div>
              {alert.updated_by && (
                <div className={classes.metadataItem}>
                  <IconUser size={14} />
                  <span>Updated by <strong>{alert.updated_by}</strong></span>
                </div>
              )}
              <div className={classes.metadataItem}>
                <IconCalendar size={14} />
                <span>Last updated {formatDateTime(alert.updated_at)}</span>
              </div>
            </div>
          </section>
        </div>

        {/* Right Column - Evaluation History */}
        <aside className={classes.sidebar}>
          <section className={classes.section}>
            <h2 className={classes.sectionTitle}><IconClock size={18} /> Evaluation History</h2>
            <div className={classes.historyContainer}>
              {isHistoryLoading ? (
                <div className={classes.historyLoader}><Loader size="sm" color="teal" /><span>Loading...</span></div>
              ) : evaluationHistoryData?.error ? (
                <div className={classes.historyError}>{evaluationHistoryData.error.message}</div>
              ) : evaluationHistoryData?.data?.length ? (
                <div className={classes.timeline}>
                  {evaluationHistoryData.data.slice(0, 10).map((item, idx) => {
                    const isItemFiring = item.current_state === "FIRING";
                    const scopeReadings = parseReadings(item.reading);
                    const thresholdPercent = (item.threshold * 100).toFixed(1);
                    return (
                      <div key={idx} className={classes.timelineItem}>
                        <div className={classes.timelineDot} style={{ background: isItemFiring ? "#ef4444" : "#10b981" }} />
                        <div className={classes.timelineContent}>
                          <div className={classes.timelineHeader}>
                            <Badge size="xs" variant="light" color={isItemFiring ? "red" : "green"}>{item.current_state}</Badge>
                            <span className={classes.timelineTime}>{formatTime(item.evaluated_at)}</span>
                          </div>
                          
                          {/* Per-Scope Readings */}
                          <div className={classes.scopeReadings}>
                            <div className={classes.scopeReadingsHeader}>
                              <span>Scope</span>
                              <span>Reading</span>
                              <span>Threshold</span>
                            </div>
                            {scopeReadings.map((sr, srIdx) => {
                              const readingPercent = (sr.reading * 100).toFixed(2);
                              const isAboveThreshold = sr.reading > item.threshold;
                              return (
                                <div key={srIdx} className={classes.scopeReadingRow}>
                                  <span className={classes.scopeName}>{sr.useCaseId}</span>
                                  <span className={`${classes.scopeValue} ${isAboveThreshold ? classes.scopeValueFiring : classes.scopeValueNormal}`}>
                                    {readingPercent}%
                                  </span>
                                  <span className={classes.scopeThreshold}>{thresholdPercent}%</span>
                                </div>
                              );
                            })}
                          </div>

                          <span className={classes.timelineDate}>{formatDate(item.evaluated_at)}</span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className={classes.historyEmpty}>No evaluation history yet</div>
              )}
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}
