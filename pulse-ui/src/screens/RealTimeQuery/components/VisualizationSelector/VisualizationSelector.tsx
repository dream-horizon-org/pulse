import { Box, SegmentedControl, Group, Switch, Text, Paper, Stack } from "@mantine/core";
import {
  IconChartLine,
  IconChartBar,
  IconChartArea,
  IconChartPie,
  IconTable,
} from "@tabler/icons-react";
import { VisualizationConfig, ChartType } from "../../RealTimeQuery.interface";
import classes from "./VisualizationSelector.module.css";

interface VisualizationSelectorProps {
  value: VisualizationConfig;
  onChange: (value: VisualizationConfig) => void;
  hasGroupBy: boolean;
}

export function VisualizationSelector({ value, onChange, hasGroupBy }: VisualizationSelectorProps) {
  const handleChartTypeChange = (chartType: string) => {
    onChange({
      ...value,
      chartType: chartType as ChartType,
    });
  };

  const handleStackedChange = (stacked: boolean) => {
    onChange({
      ...value,
      stacked,
    });
  };

  const handleLegendChange = (showLegend: boolean) => {
    onChange({
      ...value,
      showLegend,
    });
  };

  const chartData = [
    { value: "line", label: <IconChartLine size={18} /> },
    { value: "bar", label: <IconChartBar size={18} /> },
    { value: "area", label: <IconChartArea size={18} /> },
    { value: "pie", label: <IconChartPie size={18} /> },
    { value: "table", label: <IconTable size={18} /> },
  ];

  const supportsStacked = ["bar", "area"].includes(value.chartType);
  const supportsLegend = ["line", "bar", "area", "pie"].includes(value.chartType);

  return (
    <Box className={classes.container}>
      <Stack gap="sm">
        <SegmentedControl
          value={value.chartType}
          onChange={handleChartTypeChange}
          data={chartData}
          className={classes.chartSelector}
          size="sm"
        />
        
        {value.chartType !== "table" && (
          <Paper className={classes.optionsCard} p="sm" withBorder>
            <Stack gap="xs">
              {supportsStacked && (
                <Group justify="space-between">
                  <Text size="xs">Stacked</Text>
                  <Switch
                    size="xs"
                    checked={value.stacked}
                    onChange={(e) => handleStackedChange(e.currentTarget.checked)}
                    color="teal"
                  />
                </Group>
              )}
              {supportsLegend && (
                <Group justify="space-between">
                  <Text size="xs">Show Legend</Text>
                  <Switch
                    size="xs"
                    checked={value.showLegend}
                    onChange={(e) => handleLegendChange(e.currentTarget.checked)}
                    color="teal"
                  />
                </Group>
              )}
            </Stack>
          </Paper>
        )}
      </Stack>
    </Box>
  );
}

