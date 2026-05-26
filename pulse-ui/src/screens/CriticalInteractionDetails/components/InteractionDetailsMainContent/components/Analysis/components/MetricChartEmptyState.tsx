import { Box, Text, useMantineTheme } from "@mantine/core";
import { IconCircleCheck } from "@tabler/icons-react";

interface MetricChartEmptyStateProps {
  message: string;
  height?: number;
}

export const MetricChartEmptyState: React.FC<MetricChartEmptyStateProps> = ({
  message,
  height = 360,
}) => {
  const theme = useMantineTheme();

  return (
    <Box
      style={{
        height,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        background: "rgba(34, 197, 94, 0.04)",
        borderRadius: "12px",
        border: "1px dashed rgba(34, 197, 94, 0.25)",
      }}
    >
      <IconCircleCheck size={48} color={theme.colors.green[6]} stroke={1.5} />
      <Text size="sm" c="dimmed" mt="sm" ta="center" px="md">
        {message}
      </Text>
    </Box>
  );
};
