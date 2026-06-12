// @ts-nocheck

import { Box, Grid, Typography } from "@mui/material";
import {
  BarChart,
  Bar,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from "recharts";
import ChartCard from "./ChartCard";

/**
 * Reusable section for metric breakdowns across all dimensions
 */
const MetricBreakdownSection: React.FC<any> = ({
  title,
  icon,
  color,
  metric,
  platformData,
  appVersionData,
  deviceModelData,
  osVersionData,
  geographicData,
  networkTypeData,
  networkProviderData,
  showSecondaryMetric = false,
  secondaryMetric = null,
  secondaryColor = null,
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
          bgcolor: `${color}.lighter`,
          borderRadius: 1,
        }}
      >
        {icon}
        <Typography variant="h5" sx={{ fontWeight: 600 }}>
          {title}
        </Typography>
      </Box>

      <Grid container spacing={3}>
        {/* By Platform */}
        <Grid item xs={12} md={4}>
          <ChartCard
            title={`${title.split(":")[1] || title} by Platform Type`}
            height={280}
          >
            <BarChart data={platformData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="platform" />
              <YAxis />
              <Tooltip />
              {showSecondaryMetric && <Legend />}
              <Bar
                dataKey={metric}
                fill={
                  color === "primary"
                    ? "#10b981"
                    : color === "error"
                      ? "#ef4444"
                      : color === "warning"
                        ? "#3b82f6"
                        : "#06b6d4"
                }
                name={title.split(":")[1]?.trim() || metric}
              />
              {showSecondaryMetric && secondaryMetric && (
                <Bar
                  dataKey={secondaryMetric}
                  fill={secondaryColor || "#f59e0b"}
                  name={secondaryMetric}
                />
              )}
            </BarChart>
          </ChartCard>
        </Grid>

        {/* By App Version */}
        <Grid item xs={12} md={4}>
          <ChartCard
            title={`${title.split(":")[1]?.trim() || title} Across App Versions`}
            height={280}
          >
            {showSecondaryMetric ? (
              <BarChart data={appVersionData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="version" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Bar
                  dataKey={metric}
                  fill={
                    color === "primary"
                      ? "#10b981"
                      : color === "error"
                        ? "#dc2626"
                        : color === "warning"
                          ? "#3b82f6"
                          : "#3b82f6"
                  }
                  name={metric}
                />
                {secondaryMetric && (
                  <Bar
                    dataKey={secondaryMetric}
                    fill={secondaryColor || "#f97316"}
                    name={secondaryMetric}
                  />
                )}
              </BarChart>
            ) : (
              <LineChart data={appVersionData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="version" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Line
                  type="monotone"
                  dataKey={metric}
                  stroke={
                    color === "primary"
                      ? "#3b82f6"
                      : color === "error"
                        ? "#ef4444"
                        : color === "warning"
                          ? "#3b82f6"
                          : "#06b6d4"
                  }
                  strokeWidth={2}
                  name={metric}
                />
              </LineChart>
            )}
          </ChartCard>
        </Grid>

        {/* By Network Type */}
        <Grid item xs={12} md={4}>
          <ChartCard
            title={`${title.split(":")[1]?.trim() || title} by Network Type`}
            height={280}
          >
            <BarChart data={networkTypeData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="type" />
              <YAxis />
              <Tooltip />
              {showSecondaryMetric && <Legend />}
              <Bar
                dataKey={metric}
                fill={
                  color === "primary"
                    ? "#06b6d4"
                    : color === "error"
                      ? "#f59e0b"
                      : color === "warning"
                        ? "#f59e0b"
                        : "#3b82f6"
                }
                name={metric}
              />
              {showSecondaryMetric && secondaryMetric && (
                <Bar
                  dataKey={secondaryMetric}
                  fill={secondaryColor || "#f59e0b"}
                  name={secondaryMetric}
                />
              )}
            </BarChart>
          </ChartCard>
        </Grid>

        {/* By Device Model */}
        <Grid item xs={12}>
          <ChartCard
            title={`${title.split(":")[1]?.trim() || title} Across Device Models`}
            height={320}
          >
            <BarChart data={deviceModelData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis
                dataKey="device"
                angle={-20}
                textAnchor="end"
                height={100}
                tick={{ fontSize: 10 }}
              />
              <YAxis />
              <Tooltip />
              {showSecondaryMetric && <Legend />}
              <Bar
                dataKey={metric}
                fill={
                  color === "primary"
                    ? "#8b5cf6"
                    : color === "error"
                      ? "#ef4444"
                      : color === "warning"
                        ? "#06b6d4"
                        : "#3b82f6"
                }
                name={metric}
              />
              {showSecondaryMetric && secondaryMetric && (
                <Bar
                  dataKey={secondaryMetric}
                  fill={secondaryColor || "#f59e0b"}
                  name={secondaryMetric}
                />
              )}
            </BarChart>
          </ChartCard>
        </Grid>

        {/* By OS Version */}
        <Grid item xs={12}>
          <ChartCard
            title={`${title.split(":")[1]?.trim() || title} Across OS Versions`}
            height={320}
          >
            <BarChart data={osVersionData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis
                dataKey="version"
                angle={-20}
                textAnchor="end"
                height={80}
                tick={{ fontSize: 10 }}
              />
              <YAxis />
              <Tooltip />
              {showSecondaryMetric && <Legend />}
              <Bar
                dataKey={metric}
                fill={
                  color === "primary"
                    ? "#f59e0b"
                    : color === "error"
                      ? "#ef4444"
                      : color === "warning"
                        ? "#8b5cf6"
                        : "#8b5cf6"
                }
                name={metric}
              />
              {showSecondaryMetric && secondaryMetric && (
                <Bar
                  dataKey={secondaryMetric}
                  fill={secondaryColor || "#f59e0b"}
                  name={secondaryMetric}
                />
              )}
            </BarChart>
          </ChartCard>
        </Grid>

        {/* By Geographic Location */}
        <Grid item xs={12}>
          <ChartCard
            title={`${title.split(":")[1]?.trim() || title} by Geographic Location`}
            height={320}
          >
            <BarChart data={geographicData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis
                dataKey="location"
                angle={-20}
                textAnchor="end"
                height={80}
                tick={{ fontSize: 10 }}
              />
              <YAxis />
              <Tooltip />
              {showSecondaryMetric && <Legend />}
              <Bar
                dataKey={metric}
                fill={
                  color === "primary"
                    ? "#ec4899"
                    : color === "error"
                      ? "#dc2626"
                      : color === "warning"
                        ? "#ec4899"
                        : "#06b6d4"
                }
                name={metric}
              />
              {showSecondaryMetric && secondaryMetric && (
                <Bar
                  dataKey={secondaryMetric}
                  fill={secondaryColor || "#f59e0b"}
                  name={secondaryMetric}
                />
              )}
            </BarChart>
          </ChartCard>
        </Grid>

        {/* By Network Provider */}
        <Grid item xs={12} md={4}>
          <ChartCard
            title={`${title.split(":")[1]?.trim() || title} by Network Providers`}
            height={280}
          >
            <BarChart data={networkProviderData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="provider" />
              <YAxis />
              <Tooltip />
              {showSecondaryMetric && <Legend />}
              <Bar
                dataKey={metric}
                fill={
                  color === "primary"
                    ? "#84cc16"
                    : color === "error"
                      ? "#ef4444"
                      : color === "warning"
                        ? "#3b82f6"
                        : "#3b82f6"
                }
                name={metric}
              />
              {showSecondaryMetric && secondaryMetric && (
                <Bar
                  dataKey={secondaryMetric}
                  fill={secondaryColor || "#f97316"}
                  name={secondaryMetric}
                />
              )}
            </BarChart>
          </ChartCard>
        </Grid>
      </Grid>
    </Box>
  );
};

export default MetricBreakdownSection;
