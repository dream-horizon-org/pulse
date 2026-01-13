import { Card, Text, Box, useMantineTheme, Grid } from "@mantine/core";
import {
  BarChart,
  CustomToolTip,
} from "../../../../../../../components/Charts";

export interface ChartConfig<T = any> {
  title: string;
  description: string;
  data: T[];
  yAxisDataKey: string;
  valueKey: string;
  seriesName: string;
  xAxisName?: string;
  labelFormatter?: (p: { value: number }) => string;
}

export interface SectionConfig {
  title: string;
  description: string;
  charts: ChartConfig[];
}

interface TopIssuesChartsProps {
  sections: SectionConfig[];
}

const cardStyle = {
  background: "linear-gradient(145deg, #ffffff 0%, #fafbfc 100%)",
  border: "1px solid rgba(14, 201, 194, 0.12)",
  borderRadius: "16px",
  boxShadow:
    "0 4px 12px rgba(14, 201, 194, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.8)",
  transition: "all 0.4s cubic-bezier(0.4, 0, 0.2, 1)",
  height: "100%",
  position: "relative" as const,
  overflow: "hidden" as const,
};

const TopIssuesCharts: React.FC<TopIssuesChartsProps> = ({ sections }) => {
  const theme = useMantineTheme();

  const getColorByIntensity = (normalized: number) => {
    if (normalized > 0.7) return theme.colors.red[4];
    if (normalized > 0.4) return theme.colors.orange[4];
    if (normalized > 0.3) return theme.colors.yellow[5];
    return theme.colors.green[4];
  };

  const createChartOption = (chart: ChartConfig) => {
    const maxValue = Math.max(...chart.data.map((d) => Number(d[chart.valueKey]) || 0));

    return {
      grid: { left: 0, right: 16, top: 24, bottom: 40 },
      tooltip: { ...CustomToolTip, axisPointer: { type: "shadow" } },
      xAxis: {
        type: "value",
        ...(chart.xAxisName && {
          name: chart.xAxisName,
          nameLocation: "middle",
          nameGap: 20,
          nameTextStyle: { fontSize: 14, fontFamily: theme.fontFamily },
        }),
        max: Math.ceil(maxValue / 500) * 500 || 500,
      },
      yAxis: {
        type: "category",
        data: chart.data.map((d) => d[chart.yAxisDataKey]),
        axisLabel: { fontSize: 11 },
      },
      series: [
        {
          name: chart.seriesName,
          type: "bar",
          data: chart.data.map((d) => ({
            value: d[chart.valueKey],
            itemStyle: {
              color: getColorByIntensity(
                maxValue > 0 ? Number(d[chart.valueKey]) / maxValue : 0
              ),
            },
          })),
          label: {
            show: true,
            position: "right",
            fontSize: 11,
            fontWeight: 600,
            ...(chart.labelFormatter && { formatter: chart.labelFormatter }),
          },
          barMaxWidth: 18,
        },
      ],
    };
  };

  return (
    <Box>
      {sections.map((section, sectionIndex) => (
        <Box key={section.title} mt={sectionIndex > 0 ? "xl" : 0}>
          {/* <Box mb="md">
            <Text
              size="sm"
              fw={700}
              c="#0ba09a"
              mb={4}
              style={{ fontSize: "16px", letterSpacing: "-0.3px" }}
            >
              {section.title}
            </Text>
            <Text size="xs" c="dimmed" style={{ fontSize: "12px" }}>
              {section.description}
            </Text>
          </Box> */}
          <Grid gutter="sm">
            {section.charts
              .filter((chart) => chart.data && chart.data.length > 0)
              .map((chart) => (
              <Grid.Col key={chart.title} span={{ base: 12, md: 4 }}>
                <Card padding="md" withBorder radius="md" style={cardStyle}>
                  <Text
                    size="sm"
                    fw={700}
                    c="#0ba09a"
                    mb={4}
                    style={{ fontSize: "14px", letterSpacing: "-0.2px" }}
                  >
                    {chart.title}
                  </Text>
                  <Text c="dimmed" size="xs" mb="sm" style={{ fontSize: "12px" }}>
                    {chart.description}
                  </Text>
                  <Box style={{ height: 360 }}>
                    <BarChart
                      option={createChartOption(chart)}
                      height={360}
                      withLegend={false}
                    />
                  </Box>
                </Card>
              </Grid.Col>
            ))}
          </Grid>
        </Box>
      ))}
    </Box>
  );
};

export default TopIssuesCharts;
