import { Box, Text, Paper } from "@mantine/core";
import { LineChart } from "../../../components/Charts";
import classes from "./PerformanceChart.module.css";

interface PerformanceChartProps {
  title: string;
  data: any[];
  dataKey: string;
  color: string;
  domain: [number, number];
  unit: string;
}

export const PerformanceChart: React.FC<PerformanceChartProps> = ({
  title,
  data,
  dataKey,
  color,
  domain,
  unit,
}) => {
  return (
    <Paper className={classes.chartCard}>
      <Box className={classes.topAccent} />
      <Text className={classes.chartTitle}>{title}</Text>
      <Box style={{ height: 200, width: "100%" }}>
        <LineChart
          height={200}
          option={{
            xAxis: {
              type: "category",
              data: data.map((d) => d.time),
            },
            yAxis: {
              type: "value",
              min: domain[0],
              max: domain[1],
            },
            series: [
              {
                name: title,
                color: color,
                data: data.map((d) => d[dataKey]),
              },
            ],
          }}
        />
      </Box>
    </Paper>
  );
};
