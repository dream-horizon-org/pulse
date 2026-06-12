// @ts-nocheck

import { Box, Grid, Typography } from "@mui/material";
import { DeviceHub } from "@mui/icons-material";
import {
  ComposedChart,
  Bar,
  Line,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from "recharts";
import ChartCard from "./ChartCard";

/**
 * Combined Multi-Metric Analysis Section
 */
const CombinedMetricsSection = ({
  deviceModelData,
  appVersionData,
  networkTypeData,
  userSatisfactionByPlatform,
  osVersionData,
}) => {
  return (
    <Box sx={{ mb: 4 }}>
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          gap: 1,
          mb: 3,
          p: 2,
          bgcolor: "success.lighter",
          borderRadius: 1,
        }}
      >
        <DeviceHub sx={{ color: "success.main", fontSize: 32 }} />
        <Typography variant="h5" sx={{ fontWeight: 600 }}>
          SECTION 2: Combined Multi-Metric Analysis
        </Typography>
      </Box>

      <Grid container spacing={3}>
        {/* Apdex vs Latency by Device */}
        <Grid item xs={12}>
          <ChartCard
            title="Apdex vs. Latency Across Top 10 Device Models"
            height={340}
          >
            <ComposedChart data={deviceModelData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis
                dataKey="device"
                angle={-20}
                textAnchor="end"
                height={100}
                tick={{ fontSize: 10 }}
              />
              <YAxis
                yAxisId="left"
                domain={[0, 1]}
                label={{ value: "Apdex", angle: -90, position: "insideLeft" }}
              />
              <YAxis
                yAxisId="right"
                orientation="right"
                label={{
                  value: "Latency (ms)",
                  angle: 90,
                  position: "insideRight",
                }}
              />
              <Tooltip />
              <Legend />
              <Bar
                yAxisId="left"
                dataKey="apdex"
                fill="#10b981"
                name="Apdex Score"
              />
              <Line
                yAxisId="right"
                type="monotone"
                dataKey="latency"
                stroke="#ef4444"
                strokeWidth={2}
                name="Latency (ms)"
              />
            </ComposedChart>
          </ChartCard>
        </Grid>

        {/* Crashes vs Frozen Frames by App Version */}
        <Grid item xs={12}>
          <ChartCard
            title="Crashes vs. Frozen Frames Across App Versions"
            height={320}
          >
            <ComposedChart data={appVersionData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="version" />
              <YAxis
                yAxisId="left"
                label={{ value: "Crashes", angle: -90, position: "insideLeft" }}
              />
              <YAxis
                yAxisId="right"
                orientation="right"
                label={{
                  value: "Frozen Frames",
                  angle: 90,
                  position: "insideRight",
                }}
              />
              <Tooltip />
              <Legend />
              <Bar
                yAxisId="left"
                dataKey="crashes"
                fill="#ef4444"
                name="Crashes"
              />
              <Area
                yAxisId="right"
                type="monotone"
                dataKey="frozenFrames"
                fill="#3b82f6"
                stroke="#3b82f6"
                fillOpacity={0.3}
                name="Frozen Frames"
              />
            </ComposedChart>
          </ChartCard>
        </Grid>

        {/* Network Performance vs Latency by Network Type */}
        <Grid item xs={12} md={4}>
          <ChartCard
            title="Network Performance vs. Latency by Network Type"
            height={320}
          >
            <ComposedChart data={networkTypeData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="type" />
              <YAxis yAxisId="left" domain={[0, 1]} />
              <YAxis yAxisId="right" orientation="right" />
              <Tooltip />
              <Legend />
              <Bar yAxisId="left" dataKey="apdex" fill="#10b981" name="Apdex" />
              <Line
                yAxisId="right"
                type="monotone"
                dataKey="latency"
                stroke="#f59e0b"
                strokeWidth={2}
                name="Latency (ms)"
              />
            </ComposedChart>
          </ChartCard>
        </Grid>

        {/* User Satisfaction by Platform */}
        <Grid item xs={12} md={4}>
          <ChartCard
            title="User Satisfaction Distribution by Platform"
            height={320}
          >
            <ComposedChart data={userSatisfactionByPlatform}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="platform" />
              <YAxis />
              <Tooltip />
              <Legend />
              <Bar dataKey="poor" stackId="a" fill="#ef4444" name="Poor" />
              <Bar
                dataKey="average"
                stackId="a"
                fill="#f59e0b"
                name="Average"
              />
              <Bar dataKey="good" stackId="a" fill="#84cc16" name="Good" />
              <Bar
                dataKey="excellent"
                stackId="a"
                fill="#10b981"
                name="Excellent"
              />
            </ComposedChart>
          </ChartCard>
        </Grid>

        {/* Error Rate vs Crashes by OS Version */}
        <Grid item xs={12} md={4}>
          <ChartCard title="Error Rate vs. Crashes by OS Versions" height={320}>
            <ComposedChart data={osVersionData.slice(0, 6)}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis
                dataKey="version"
                angle={-20}
                textAnchor="end"
                height={80}
                tick={{ fontSize: 10 }}
              />
              <YAxis
                yAxisId="left"
                label={{
                  value: "Error Rate (%)",
                  angle: -90,
                  position: "insideLeft",
                  fontSize: 11,
                }}
              />
              <YAxis
                yAxisId="right"
                orientation="right"
                label={{
                  value: "Crashes",
                  angle: 90,
                  position: "insideRight",
                  fontSize: 11,
                }}
              />
              <Tooltip />
              <Legend />
              <Line
                yAxisId="left"
                type="monotone"
                dataKey="errorRate"
                stroke="#ef4444"
                strokeWidth={2}
                name="Error Rate (%)"
              />
              <Bar
                yAxisId="right"
                dataKey="crashes"
                fill="#dc2626"
                name="Crashes"
              />
            </ComposedChart>
          </ChartCard>
        </Grid>
      </Grid>
    </Box>
  );
};

export default CombinedMetricsSection;
