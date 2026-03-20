import { Card, Stack, Text, Group, Badge, Progress, SimpleGrid } from "@mantine/core";
import { ReactNode } from "react";
import { IconTrendingUp, IconTrendingDown } from "@tabler/icons-react";
import { DrillDownLink, DrillDownButton } from "./DrillDownActions";
import classes from "./InsightsDashboard.module.css";

/**
 * Reusable Metric Card Components for Session Replay Insights
 * Provides consistent design patterns for displaying metrics with drill-down capability
 */

// ============================================
// 1. Standard Metric Card
// ============================================
interface MetricCardProps {
  /** Label for the metric */
  label: string;
  /** Main value to display */
  value: string | number;
  /** Optional secondary text (below value) */
  description?: string;
  /** Optional icon */
  icon?: ReactNode;
  /** Color for the value text */
  valueColor?: string;
  /** Badge to display (optional) */
  badge?: ReactNode;
  /** Click handler for drill-down */
  onClick: () => void;
  /** Show inline link or button */
  actionType?: 'link' | 'button';
  /** Custom action label */
  actionLabel?: string;
  /** Padding size */
  padding?: 'sm' | 'md' | 'lg';
}

export function MetricCard({
  label,
  value,
  description,
  icon,
  valueColor,
  badge,
  onClick,
  actionType = 'link',
  actionLabel,
  padding = 'md'
}: MetricCardProps) {
  return (
    <Card
      padding={padding}
      radius="md"
      withBorder
      className={classes.metricCard}
      onClick={onClick}
    >
      <Stack gap="xs">
        {/* Label with optional icon */}
        {icon ? (
          <Group gap="xs">
            {icon}
            <Text size="xs" c="dimmed" tt="uppercase">{label}</Text>
          </Group>
        ) : (
          <Text size="xs" c="dimmed" tt="uppercase" fw={600}>{label}</Text>
        )}

        {/* Main Value */}
        <Text size="xl" fw={700} c={valueColor}>
          {typeof value === 'number' ? value.toLocaleString() : value}
        </Text>

        {/* Description */}
        {description && (
          <Text size="xs" c="dimmed">{description}</Text>
        )}

        {/* Optional Badge */}
        {badge}

        {/* Action */}
        {actionType === 'link' ? (
          <DrillDownLink label={actionLabel} onClick={onClick} />
        ) : (
          <DrillDownButton label={actionLabel} onClick={onClick} fullWidth size="sm" />
        )}
      </Stack>
    </Card>
  );
}

// ============================================
// 2. Comparison Metric Card (with trend badge)
// ============================================
interface ComparisonMetricCardProps {
  /** Label for the metric */
  label: string;
  /** Current value */
  currentValue: number;
  /** Change percentage */
  changePercent: number;
  /** Is positive change good? */
  positiveIsGood?: boolean;
  /** Optional percentage to show next to value */
  displayPercent?: number;
  /** Comparison period label */
  comparisonLabel?: string;
  /** Click handler */
  onClick: () => void;
  /** Custom action label */
  actionLabel?: string;
}

export function ComparisonMetricCard({
  label,
  currentValue,
  changePercent,
  positiveIsGood = true,
  displayPercent,
  comparisonLabel = 'vs last period',
  onClick,
  actionLabel
}: ComparisonMetricCardProps) {
  const isPositiveChange = changePercent >= 0;
  const isGoodChange = positiveIsGood ? isPositiveChange : !isPositiveChange;
  const trendColor = isGoodChange ? 'teal' : 'red';

  return (
    <Card
      padding="lg"
      radius="md"
      className={classes.metricCard}
      onClick={onClick}
    >
      <Stack gap="xs">
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">{label}</Text>
        
        <Group gap="xs" align="baseline">
          <Text size="xl" fw={700}>
            {currentValue.toLocaleString()}
          </Text>
          {displayPercent !== undefined && (
            <Text size="sm" c="dimmed">({displayPercent.toFixed(1)}%)</Text>
          )}
        </Group>

        <Group gap="xs">
          <Badge
            size="sm"
            color={trendColor}
            variant="light"
            leftSection={isPositiveChange ? <IconTrendingUp size={12} /> : <IconTrendingDown size={12} />}
          >
            {isPositiveChange ? '+' : ''}{changePercent.toFixed(1)}%
          </Badge>
          <Text size="xs" c="dimmed">{comparisonLabel}</Text>
        </Group>

        <DrillDownLink label={actionLabel} onClick={onClick} />
      </Stack>
    </Card>
  );
}

// ============================================
// 3. Status Metric Card (with health indicator)
// ============================================
interface StatusMetricCardProps {
  /** Label for the metric */
  label: string;
  /** Main value */
  value: number | string;
  /** Status/health indicator */
  status: 'excellent' | 'good' | 'fair' | 'poor';
  /** Optional sub-metrics */
  subMetrics?: Array<{ label: string; value: string | number }>;
  /** Optional progress bar value (0-100) */
  progressValue?: number;
  /** Click handler */
  onClick: () => void;
  /** Custom action label */
  actionLabel?: string;
}

export function StatusMetricCard({
  label,
  value,
  status,
  subMetrics,
  progressValue,
  onClick,
  actionLabel = 'View Sessions'
}: StatusMetricCardProps) {
  const getStatusColor = (s: string) => {
    switch (s) {
      case 'excellent': return 'teal';
      case 'good': return 'lime';
      case 'fair': return 'yellow';
      case 'poor': return 'red';
      default: return 'gray';
    }
  };

  const statusColor = getStatusColor(status);

  return (
    <Card
      padding="lg"
      radius="md"
      withBorder
      className={classes.flowCard}
    >
      <Stack gap="md">
        <Group justify="space-between" wrap="nowrap">
          <Text size="sm" fw={600} style={{ flex: 1 }}>{label}</Text>
          <Badge size="sm" color={statusColor} variant="light">
            {status}
          </Badge>
        </Group>

        <Stack gap="xs" align="center" py="md">
          <Text size="48px" fw={700} lh={1} c={statusColor}>
            {typeof value === 'number' ? value.toFixed(2) : value}
          </Text>
        </Stack>

        {progressValue !== undefined && (
          <Progress 
            value={progressValue}
            size="md"
            radius="md"
            color={statusColor}
          />
        )}

        {subMetrics && subMetrics.length > 0 && (
          <SimpleGrid cols={subMetrics.length} spacing="xs">
            {subMetrics.map((metric, idx) => (
              <Stack key={idx} gap={4}>
                <Text size="xs" c="dimmed">{metric.label}</Text>
                <Text size="sm" fw={600}>
                  {typeof metric.value === 'number' ? metric.value.toLocaleString() : metric.value}
                </Text>
              </Stack>
            ))}
          </SimpleGrid>
        )}

        <DrillDownButton 
          label={actionLabel}
          onClick={onClick}
          color={statusColor}
          fullWidth
        />
      </Stack>
    </Card>
  );
}
