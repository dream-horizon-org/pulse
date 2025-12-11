import { Text } from "@mantine/core";
import classes from "./InteractionCard.module.css";

interface InteractionCardProps {
  interactionName: string;
  description?: string;
  onClick?: () => void;
  // Metric values
  apdexScore?: number;
  errorRateValue?: number;
  p50Latency?: number;
  poorUserPercentage?: number;
}

export function InteractionCard({
  interactionName,
  description,
  onClick,
  apdexScore = 0,
  errorRateValue = 0,
  p50Latency = 0,
  poorUserPercentage = 0,
}: InteractionCardProps) {
  // Use provided metric values or defaults

  const getHealthColor = (apdexScore: number) => {
    if (apdexScore >= 0.8) return "#10b981";  // Green - matches "Excellent"
    if (apdexScore >= 0.6) return "#f59e0b";  // Amber - matches "Good"
    if (apdexScore >= 0.4) return "#f97316";  // Orange - matches "Fair"
    return "#ef4444";                          // Red - matches "Poor"
  };

  const getHealthGradient = (apdexScore: number) => {
    if (apdexScore >= 0.8)
      return "linear-gradient(135deg, rgba(16, 185, 129, 0.06), rgba(16, 185, 129, 0.12))";
    if (apdexScore >= 0.6)
      return "linear-gradient(135deg, rgba(245, 158, 11, 0.06), rgba(245, 158, 11, 0.12))";
    if (apdexScore >= 0.4)
      return "linear-gradient(135deg, rgba(249, 115, 22, 0.06), rgba(249, 115, 22, 0.12))";
    return "linear-gradient(135deg, rgba(239, 68, 68, 0.06), rgba(239, 68, 68, 0.12))";
  };

  const getHealthStatus = (apdexScore: number) => {
    if (apdexScore >= 0.8) return "Excellent";
    if (apdexScore >= 0.6) return "Good";
    if (apdexScore >= 0.4) return "Fair";
    return "Poor";
  };

  const healthColor = getHealthColor(apdexScore);
  const healthStatus = getHealthStatus(apdexScore);
  const healthGradient = getHealthGradient(apdexScore);

  const formattedLatency = (latency: number) => {
    if (latency > 1000) {
      return `${(latency / 1000).toFixed(2)}s`;
    }
    return `${latency.toFixed(2)}ms`; 
  };

  const formattedErrorRate = (errorRate: number) => {
    return `${(errorRate).toFixed(2)}%`;
  };

  const formattedPoorUserPercentage = (poorUserPercentage: number) => {
    return `${poorUserPercentage.toFixed(2)}%`;
  };

  return (
    <div
      className={classes.interactionCard}
      onClick={onClick}
      style={{
        background: `linear-gradient(145deg, #ffffff 0%, #fafbfc 100%), ${healthGradient}`,
        backgroundBlendMode: "normal, multiply",
      }}
    >
      <div className={classes.interactionHeader}>
        <div className={classes.interactionInfo}>
          <Text className={classes.interactionName}>{interactionName}</Text>
          {description && (
            <Text className={classes.interactionDescription}>
              {description}
            </Text>
          )}
        </div>
        <div
          className={classes.healthBadge}
          style={{
            backgroundColor: `${healthColor}20`,
            color: healthColor,
            borderColor: `${healthColor}40`,
          }}
        >
          {healthStatus}
        </div>
      </div>

      <div className={classes.metricsGrid}>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Apdex</Text>
          <Text className={classes.metricValue} style={{ color: healthColor }}>
            {apdexScore > 0 ? apdexScore.toFixed(2) : "N/A"}
          </Text>
        </div>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Error Rate</Text>
          <Text className={classes.metricValue}>
            {errorRateValue >= 0 ? formattedErrorRate(errorRateValue) : "N/A"}
          </Text>
        </div>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>P50</Text>
          <Text className={classes.metricValue}>
            {p50Latency > 0 ? formattedLatency(p50Latency) : "N/A"}
          </Text>
        </div>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Poor Users</Text>
          <Text className={classes.metricValue}>
            {poorUserPercentage >= 0
              ? formattedPoorUserPercentage(poorUserPercentage)
              : "N/A"}
          </Text>
        </div>
      </div>

      <div
        className={classes.healthBar}
        style={{
          background: `linear-gradient(to right, ${healthColor} ${apdexScore * 100}%, var(--mantine-color-gray-2) ${apdexScore * 100}%)`,
        }}
      />
    </div>
  );
}
