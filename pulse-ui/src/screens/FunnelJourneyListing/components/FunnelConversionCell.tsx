import { Group, Text } from "@mantine/core";
import { IconTrendingDown, IconTrendingUp } from "@tabler/icons-react";
import type { FunnelListItem } from "../../../services/funnels.service";

export function FunnelConversionCell({ row }: { row: FunnelListItem }) {
  if (
    row.status === "IN_PROGRESS" ||
    row.status === "PENDING" ||
    row.overallConversionRate == null
  ) {
    return (
      <Text size="sm" c="dimmed">
        —
      </Text>
    );
  }
  const rate = row.overallConversionRate;
  const trend = row.conversionTrend;

  if (trend == null || trend === 0) {
    return (
      <Text size="sm" fw={600} ta="left">
        {rate.toFixed(1)}%
      </Text>
    );
  }

  const up = trend > 0;

  return (
    <Group gap={8} align="center" wrap="nowrap">
      <Text size="sm" fw={600} ta="left">
        {rate.toFixed(1)}%
      </Text>
      <Group gap={4} align="center" wrap="nowrap">
        {up ? (
          <IconTrendingUp
            size={14}
            style={{ color: "var(--mantine-color-teal-6)" }}
            aria-hidden
          />
        ) : (
          <IconTrendingDown
            size={14}
            style={{ color: "var(--mantine-color-red-6)" }}
            aria-hidden
          />
        )}
        <Text
          size="sm"
          fw={500}
          c={up ? "teal.7" : "red.7"}
          style={{ whiteSpace: "nowrap" }}
        >
          {up ? "+" : ""}
          {trend.toFixed(1)}%
        </Text>
      </Group>
    </Group>
  );
}
