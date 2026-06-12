import { Card, Box, Text } from "@mantine/core";
import { IconTrendingUp, IconTrendingDown } from "@tabler/icons-react";
import type { KPICardProps } from "./KPICard.interface";

/**
 * KPI Card Component
 * Displays a single key performance indicator with trend
 */
const KPICard: React.FC<KPICardProps> = ({
  title,
  value,
  unit,
  trend,
  trendValue,
  icon,
  color = "blue",
}) => {
  const isPositive = trend === "up";
  const isNegative = trend === "down";

  return (
    <Card
      shadow="sm"
      p="md"
      style={{
        height: "100%",
        background: "linear-gradient(145deg, #ffffff 0%, #fafbfc 100%)",
        border: "1px solid rgba(14, 201, 194, 0.12)",
        borderRadius: "16px",
        boxShadow:
          "0 4px 12px rgba(14, 201, 194, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.8)",
        transition: "all 0.4s cubic-bezier(0.4, 0, 0.2, 1)",
        cursor: "pointer",
        position: "relative",
        overflow: "hidden",
      }}
      onMouseEnter={(e) => {
        (e.currentTarget as HTMLDivElement).style.transform =
          "translateY(-4px) scale(1.01)";
        (e.currentTarget as HTMLDivElement).style.boxShadow =
          "0 12px 24px rgba(14, 201, 194, 0.12), 0 6px 12px rgba(0, 0, 0, 0.04), inset 0 1px 0 rgba(255, 255, 255, 1)";
        (e.currentTarget as HTMLDivElement).style.borderColor =
          "rgba(14, 201, 194, 0.25)";
      }}
      onMouseLeave={(e) => {
        (e.currentTarget as HTMLDivElement).style.transform =
          "translateY(0) scale(1)";
        (e.currentTarget as HTMLDivElement).style.boxShadow =
          "0 4px 12px rgba(14, 201, 194, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.8)";
        (e.currentTarget as HTMLDivElement).style.borderColor =
          "rgba(14, 201, 194, 0.12)";
      }}
    >
      <Box
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start",
          marginBottom: 12,
        }}
      >
        <Text
          size="xs"
          style={{
            fontWeight: 500,
            textTransform: "capitalize",
            fontSize: "10px",
            color: "var(--mantine-color-dark-4)",
            letterSpacing: "0.2px",
          }}
        >
          {title}
        </Text>
        <Box
          style={{
            padding: 8,
            borderRadius: 10,
            background:
              "linear-gradient(135deg, rgba(14, 201, 194, 0.12), rgba(14, 201, 194, 0.2))",
            border: "1px solid rgba(14, 201, 194, 0.25)",
            color: "#0ba09a",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          {icon}
        </Box>
      </Box>

      <Box style={{ marginBottom: 8 }}>
        <Text
          size="xl"
          style={{
            fontWeight: 600,
            color: "var(--mantine-color-dark-8)",
            lineHeight: 1,
            fontSize: "20px",
            letterSpacing: "-0.4px",
          }}
        >
          {value}
          {unit && (
            <Text
              component="span"
              size="sm"
              style={{
                marginLeft: 4,
                color: "var(--mantine-color-dark-4)",
                fontSize: "14px",
              }}
            >
              {unit}
            </Text>
          )}
        </Text>
      </Box>

      {trend && (
        <Box style={{ display: "flex", alignItems: "center", gap: 4 }}>
          {isPositive && <IconTrendingUp size={18} color="#37b24d" />}
          {isNegative && <IconTrendingDown size={18} color="#f03e3e" />}
          <Text
            size="sm"
            style={{
              fontWeight: 600,
              color: isPositive
                ? "#37b24d"
                : isNegative
                  ? "#f03e3e"
                  : "#6c757d",
            }}
          >
            {trendValue}
          </Text>
          <Text size="xs" style={{ color: "#6c757d" }}>
            vs last period
          </Text>
        </Box>
      )}
    </Card>
  );
};

export default KPICard;
