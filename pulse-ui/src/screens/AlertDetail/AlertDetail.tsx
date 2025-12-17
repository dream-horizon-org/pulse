import { useNavigate, useParams } from "react-router-dom";
import { Button, Badge, Loader, Tooltip, useMantineTheme, Divider, Collapse, Menu } from "@mantine/core";
import {
  IconArrowLeft, IconEdit, IconBellX, IconBellRinging, IconCircleCheckFilled,
  IconSquareRoundedX, IconClock, IconUser, IconCalendar, IconActivity,
  IconBell, IconBellOff, IconAlertTriangle, IconChevronDown, IconChevronRight,
  IconClock2, IconClockHour4, IconClockHour8, IconCalendarTime, IconCalendarWeek,
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
import { useGetAlertMetrics } from "../../hooks/useGetAlertMetrics";
import { showNotification } from "../../helpers/showNotification";

const OPERATOR_SYMBOLS: Record<string, string> = {
  GREATER_THAN: ">", LESS_THAN: "<", GREATER_THAN_OR_EQUAL: "≥", LESS_THAN_OR_EQUAL: "≤", EQUAL: "=",
};

const formatDuration = (seconds: number): string => {
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
};

const formatDate = (date: string | Date) => new Date(date).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
const formatTime = (date: string | Date) => new Date(date).toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit" });
const formatDateTime = (date: string | Date) => `${formatDate(date)} at ${formatTime(date)}`;

// Snooze duration options in milliseconds
const SNOOZE_OPTIONS = [
  { label: "1 hour", duration: 60 * 60 * 1000, icon: IconClock2 },
  { label: "4 hours", duration: 4 * 60 * 60 * 1000, icon: IconClockHour4 },
  { label: "8 hours", duration: 8 * 60 * 60 * 1000, icon: IconClockHour8 },
  { label: "24 hours", duration: 24 * 60 * 60 * 1000, icon: IconClock },
  { label: "2 days", duration: 2 * 24 * 60 * 60 * 1000, icon: IconCalendarTime },
  { label: "1 week", duration: 7 * 24 * 60 * 60 * 1000, icon: IconCalendarWeek },
];

// Parse evaluation_result JSON string to get metric readings
const parseEvaluationResult = (resultStr: string): Record<string, number> => {
  try {
    return JSON.parse(resultStr);
  } catch {
    return {};
  }
};


export function AlertDetail(_props: AlertDetailProps) {
  const navigate = useNavigate();
  const theme = useMantineTheme();
  const { alertId } = useParams<{ alertId: string }>();
  const [showSnoozeLoader, setShowSnoozeLoader] = useState(false);
  const [expandedScopes, setExpandedScopes] = useState<Set<number>>(new Set());

  const { data: alertData, isLoading: isAlertLoading, refetch: refetchAlert } = useGetAlertDetails({ queryParams: { alert_id: alertId || null } });
  const { data: evaluationHistoryData, isLoading: isHistoryLoading, refetch: refetchHistory } = useGetAlertEvaluationHistory({ alertId: alertId || "" });
  const { data: scopesData } = useGetAlertScopes();
  const { data: severitiesData } = useGetAlertSeverities();
  
  // Fetch metrics based on alert's scope
  const alertScope = alertData?.data?.scope || "";
  const { data: metricsData } = useGetAlertMetrics({ scope: alertScope });

  const toggleScopeExpanded = (scopeId: number) => {
    setExpandedScopes(prev => {
      const next = new Set(prev);
      if (next.has(scopeId)) {
        next.delete(scopeId);
      } else {
        next.add(scopeId);
      }
      return next;
    });
  };

  // Helper to get latest status for a scope
  const getLatestStatus = (history: { state: string }[]) => {
    if (!history || history.length === 0) return "NO_DATA";
    return history[0].state;
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "FIRING": return "#ef4444";
      case "NORMAL": return "#10b981";
      default: return "#9ca3af";
    }
  };

  // Build lookup maps
  const scopeLabels: Record<string, string> = {};
  scopesData?.data?.scopes?.forEach((s) => { scopeLabels[s.name] = s.label; });
  
  const severityConfig: Record<number, { label: string; color: string }> = {};
  const colors = ["#ef4444", "#f59e0b", "#3b82f6"];
  const labels = ["P1 Critical", "P2 Warning", "P3 Info"];
  const severities = severitiesData?.data && Array.isArray(severitiesData.data) ? severitiesData.data : [];
  severities.forEach((s: { severity_id: number; name: number }) => {
    severityConfig[s.severity_id] = { label: labels[s.name - 1] || `P${s.name}`, color: colors[s.name - 1] || "#6b7280" };
  });

  // Build metric labels lookup from fetched metrics
  const metricLabels: Record<string, string> = {};
  metricsData?.data?.metrics?.forEach((m) => { metricLabels[m.name] = m.label; });

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
  
  const handleSnooze = (durationMs: number) => {
    if (!alertId) return;
    setShowSnoozeLoader(true);
    const now = Date.now();
    snoozeAlertMutation.mutate({ alertId, snoozeAlertRequest: { snoozeFrom: now, snoozeUntil: now + durationMs } });
  };

  const handleResume = () => {
    if (!alertId) return;
    setShowSnoozeLoader(true);
    resumeAlertMutation.mutate(alertId);
  };

  const alert = alertData?.data;
  const isFiring = alert?.status === "FIRING";
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

  console.log(alert.status);
  const StatusIcon = alert.is_snoozed ? IconBellOff : isFiring ? IconBellRinging : IconBell;
  const statusColor = alert.is_snoozed ? "#94a3b8" : isFiring || alert.status === "NO_DATA" ? "#ef4444" : "#10b981";
  const statusLabel = alert.is_snoozed ? "Snoozed" : alert.status;

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
            onClick={handleResume}
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
            <Menu shadow="md" width={200} position="bottom-end" withArrow>
              <Menu.Target>
                <Button variant="light" color="orange" loading={showSnoozeLoader} rightSection={<IconChevronDown size={14} />}>
                  <IconBellX size={18} style={{ marginRight: 6 }} />
                  Snooze
                </Button>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Label>Snooze for...</Menu.Label>
                {SNOOZE_OPTIONS.map((option) => (
                  <Menu.Item
                    key={option.label}
                    leftSection={<option.icon size={16} />}
                    onClick={() => handleSnooze(option.duration)}
                  >
                    {option.label}
                  </Menu.Item>
                ))}
              </Menu.Dropdown>
            </Menu>
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
                    <span className={classes.conditionMetric}>{metricLabels[condition.metric] || condition.metric}</span>
                    <span className={classes.conditionOperator}>{OPERATOR_SYMBOLS[condition.metric_operator] || condition.metric_operator}</span>
                  </div>
                  <Divider my="xs" color="rgba(14,201,194,0.1)" />
                  <div className={classes.thresholdsList}>
                    {Object.entries(condition.threshold || {}).map(([name, value]) => (
                      <div key={name} className={classes.thresholdItem}>
                        <span className={classes.thresholdName}>{name}</span>
                        <span className={classes.thresholdValue}>{value}</span>
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
                <div className={classes.scopeHistoryList}>
                  {evaluationHistoryData.data.map((scopeHistory, scopeIdx) => {
                    const isExpanded = expandedScopes.has(scopeHistory.scope_id);
                    const latestStatus = getLatestStatus(scopeHistory.evaluation_history);
                    const statusColor = getStatusColor(latestStatus);
                    
                    return (
                      <div key={scopeIdx} className={classes.scopeHistorySection}>
                        {/* Collapsible Header */}
                        <button 
                          className={classes.scopeHistoryHeader}
                          onClick={() => toggleScopeExpanded(scopeHistory.scope_id)}
                        >
                          <div className={classes.scopeHeaderLeft}>
                            {isExpanded ? (
                              <IconChevronDown size={16} className={classes.collapseIcon} />
                            ) : (
                              <IconChevronRight size={16} className={classes.collapseIcon} />
                            )}
                            <Badge 
                              size="sm" 
                              variant="filled" 
                              style={{ background: statusColor }}
                              className={classes.latestStatusBadge}
                            >
                              {latestStatus}
                            </Badge>
                            <Tooltip label={scopeHistory.scope_name} position="top">
                              <span className={classes.scopeNameText}>
                                {scopeHistory.scope_name.length > 25 
                                  ? `${scopeHistory.scope_name.slice(0, 25)}...` 
                                  : scopeHistory.scope_name}
                              </span>
                            </Tooltip>
                          </div>
                          <span className={classes.scopeHistoryCount}>
                            {scopeHistory.evaluation_history.length} eval{scopeHistory.evaluation_history.length !== 1 ? 's' : ''}
                          </span>
                        </button>
                        
                        {/* Collapsible Content - Timeline */}
                        <Collapse in={isExpanded}>
                          <div className={classes.timeline}>
                            {scopeHistory.evaluation_history.slice(0, 10).map((item, idx) => {
                              const isItemFiring = item.state === "FIRING";
                              const isItemNoData = item.state === "NO_DATA";
                              const itemColor = isItemFiring ? "#ef4444" : isItemNoData ? "#9ca3af" : "#10b981";
                              const evaluationResult = parseEvaluationResult(item.evaluation_result);
                              
                              return (
                                <div key={idx} className={classes.timelineItem}>
                                  <div className={classes.timelineDot} style={{ background: itemColor }} />
                                  <div className={classes.timelineContent}>
                                    <div className={classes.timelineHeader}>
                                      <Badge size="xs" variant="light" color={isItemFiring ? "red" : isItemNoData ? "gray" : "green"}>
                                        {item.state}
                                      </Badge>
                                      <span className={classes.timelineTime}>{formatTime(new Date(item.evaluated_at))}</span>
                                    </div>
                                    
                                    {/* Metric Readings */}
                                    {Object.keys(evaluationResult).length > 0 && (
                                      <div className={classes.scopeReadings}>
                                        <div className={classes.scopeReadingsHeader}>
                                          <span>Metric</span>
                                          <span>Value</span>
                                        </div>
                                        {Object.entries(evaluationResult).map(([metricKey, value], mrIdx) => (
                                          <div key={mrIdx} className={classes.scopeReadingRow}>
                                            <span className={classes.scopeName}>{metricLabels[metricKey] || metricKey}</span>
                                            <span className={`${classes.scopeValue} ${isItemFiring ? classes.scopeValueFiring : classes.scopeValueNormal}`}>
                                              {value}
                                            </span>
                                          </div>
                                        ))}
                                      </div>
                                    )}

                                    <span className={classes.timelineDate}>{formatDate(new Date(item.evaluated_at))}</span>
                                  </div>
                                </div>
                              );
                            })}
                            {scopeHistory.evaluation_history.length > 10 && (
                              <div className={classes.historyMore}>
                                +{scopeHistory.evaluation_history.length - 10} more evaluations
                              </div>
                            )}
                          </div>
                        </Collapse>
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
