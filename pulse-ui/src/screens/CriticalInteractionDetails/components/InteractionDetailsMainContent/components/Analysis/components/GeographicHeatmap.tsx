import { Card, Text, Box, Tooltip, useMantineTheme } from "@mantine/core";
import type { GeographicHeatmapProps, GeographicLocation } from "./GeographicHeatmap.interface";

/**
 * Geographic Heatmap
 * Visual heatmap showing metrics by geographic location
 */
const GeographicHeatmap: React.FC<GeographicHeatmapProps> = ({
  data,
  title,
  description,
  metricLabel,
  metricSuffix = "",
  totalUnit = "interactions",
}) => {
  const theme = useMantineTheme();
  // Order is owned by the hook (ranked regions first, then low-sample tail).
  // Color scale is computed from trusted (n >= 5) regions only — a noisy
  // low-sample outlier (e.g. 1-of-1 = 100%) would otherwise wash everything
  // else out to yellow. Falls back to all data when nothing meets the floor.
  const trustedRows = data.filter((d) => (d.total ?? Infinity) >= 5);
  const refValues = (trustedRows.length > 0 ? trustedRows : data).map((d) => d.value);
  const maxValue = Math.max(...refValues, 0);
  const minValue = Math.min(...refValues, 0);

  // Color scale: Green (zero / no issues) -> Yellow -> Orange -> Red (worst).
  // For error rate and poor users, higher values = worse = more red.
  const getColorIntensity = (value: number) => {
    // 0% is a clean "no issues here" signal regardless of dataset shape.
    if (value === 0) {
      return {
        bg: theme.colors.green[0],
        border: theme.colors.green[4],
        text: theme.colors.green[8],
      };
    }

    if (maxValue === 0) {
      return {
        bg: theme.colors.gray[0],
        border: theme.colors.gray[3],
        text: theme.colors.gray[7],
      };
    }

    if (maxValue === minValue) {
      return {
        bg: theme.colors.orange[0],
        border: theme.colors.orange[3],
        text: theme.colors.orange[7],
      };
    }

    const normalized = (value - minValue) / (maxValue - minValue);

    if (normalized > 0.7)
      return {
        bg: theme.colors.red[0],
        border: theme.colors.red[3],
        text: theme.colors.red[7],
      };
    if (normalized > 0.4)
      return {
        bg: theme.colors.orange[0],
        border: theme.colors.orange[3],
        text: theme.colors.orange[7],
      };
    return {
      bg: theme.colors.yellow[0],
      border: theme.colors.yellow[4],
      text: theme.colors.yellow[8],
    };
  };

  const renderLocationRow = (location: GeographicLocation) => {
    const colors = getColorIntensity(location.value);
    const rawPercentage = maxValue > 0 ? (location.value / maxValue) * 100 : 0;
    // Cap bar width at 100% so a low-sample outlier (rate above the trusted
    // max) doesn't overflow its row visually.
    const percentage = Math.min(100, rawPercentage).toFixed(0);
    const displayValue = metricSuffix
      ? `${location.value}${metricSuffix}`
      : location.value;
    const displayName = location.name || "Unknown";
    // Show "X of N users" / "X of N interactions" so the count is
    // unambiguous (N is total, X is the affected subset).
    const totalLabel =
      typeof location.total === "number"
        ? `${Math.round((location.value * location.total) / 100).toLocaleString()} of ${location.total.toLocaleString()} ${totalUnit}`
        : null;

    return (
      <Tooltip
        key={displayName}
        label={
          totalLabel
            ? `${displayValue} ${metricLabel} • ${totalLabel} (${percentage}% of max)`
            : `${displayValue} ${metricLabel} (${percentage}% of max)`
        }
        withArrow
      >
        <Box
          style={{
            display: "flex",
            alignItems: "center",
            gap: 12,
            padding: 8,
            backgroundColor: colors.bg,
            border: `1px solid ${colors.border}`,
            borderRadius: "10px",
            cursor: "pointer",
            transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
          }}
          onMouseEnter={(e) => {
            (e.currentTarget as HTMLDivElement).style.transform = "translateX(2px)";
            (e.currentTarget as HTMLDivElement).style.boxShadow =
              "0 4px 12px rgba(14, 201, 194, 0.1)";
          }}
          onMouseLeave={(e) => {
            (e.currentTarget as HTMLDivElement).style.transform = "translateX(0)";
            (e.currentTarget as HTMLDivElement).style.boxShadow = "none";
          }}
        >
          <Box style={{ flex: "0 0 110px" }}>
            <Text size="xs" fw={600} c={colors.text}>
              {displayName}
            </Text>
            {totalLabel && (
              <Text size="10px" c="dimmed" style={{ lineHeight: 1.2 }}>
                {totalLabel}
              </Text>
            )}
          </Box>
          <Box style={{ flex: 1 }}>
            <Box
              style={{
                height: 16,
                width: `${percentage}%`,
                backgroundColor: colors.border,
                borderRadius: "var(--mantine-radius-sm)",
                transition: "width 0.3s",
              }}
            />
          </Box>
          <Box style={{ flex: "0 0 70px", textAlign: "right" }}>
            <Text size="sm" fw={700} c={colors.text}>
              {displayValue}
            </Text>
          </Box>
        </Box>
      </Tooltip>
    );
  };

  return (
    <Card
      py="md"
      px="0"
      withBorder
      radius="md"
      style={{
        background: "linear-gradient(145deg, #ffffff 0%, #fafbfc 100%)",
        border: "1px solid rgba(14, 201, 194, 0.12)",
        borderRadius: "16px",
        boxShadow:
          "0 4px 12px rgba(14, 201, 194, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.8)",
        transition: "all 0.4s cubic-bezier(0.4, 0, 0.2, 1)",
        height: "100%",
        position: "relative",
        overflow: "hidden",
      }}
    >
      <Box mb="sm" px="md">
        <Text
          fw={700}
          size="sm"
          c="#0ba09a"
          mb={4}
          style={{ fontSize: "14px", letterSpacing: "-0.2px" }}
        >
          {title}
        </Text>
        <Text size="xs" c="dimmed" style={{ fontSize: "12px" }}>
          {description}
        </Text>
      </Box>

      <Box h={334} px="md" style={{ overflowY: "auto" }}>
        <Box style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          {data.map((location) => renderLocationRow(location))}
        </Box>
      </Box>

      <Box
        mt="sm"
        style={{
          display: "flex",
          gap: 12,
          justifyContent: "center",
          alignItems: "center",
          flexWrap: "wrap",
        }}
      >
        <Text size="xs" c="dimmed">
          Color Scale:
        </Text>
        <Box style={{ display: "flex", gap: 4, alignItems: "center" }}>
          <Box
            style={{
              width: 16,
              height: 16,
              backgroundColor: theme.colors.green[0],
              border: `1px solid ${theme.colors.green[4]}`,
            }}
          />
          <Text size="xs">None</Text>
          <Box
            style={{
              width: 16,
              height: 16,
              backgroundColor: theme.colors.yellow[0],
              border: `1px solid ${theme.colors.yellow[4]}`,
            }}
          />
          <Text size="xs">Low</Text>
          <Box
            style={{
              width: 16,
              height: 16,
              backgroundColor: theme.colors.orange[0],
              border: `1px solid ${theme.colors.orange[3]}`,
            }}
          />
          <Text size="xs">Medium</Text>
          <Box
            style={{
              width: 16,
              height: 16,
              backgroundColor: theme.colors.red[0],
              border: `1px solid ${theme.colors.red[3]}`,
            }}
          />
          <Text size="xs">High</Text>
        </Box>
      </Box>
    </Card>
  );
};

export default GeographicHeatmap;
