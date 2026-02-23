// @ts-nocheck

import {
  Box,
  Grid,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Card,
  CardContent,
  Chip,
} from "@mui/material";
import { NetworkCheck } from "@mui/icons-material";
import {
  ScatterChart,
  Scatter,
  XAxis,
  YAxis,
  ZAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";

/**
 * Three-Dimensional Multi-Metric Analysis Section
 */
const ThreeDimensionalSection = ({
  bubbleData,
  heatmapOSDevice,
  comprehensiveTableData,
}) => {
  // Helper function to get color for heatmap
  const getHeatmapColor = (value) => {
    if (value < 80) return "#10b981";
    if (value < 100) return "#84cc16";
    if (value < 130) return "#f59e0b";
    if (value < 160) return "#ef4444";
    return "#dc2626";
  };

  return (
    <Box sx={{ mb: 4 }}>
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          gap: 1,
          mb: 3,
          p: 2,
          bgcolor: "secondary.lighter",
          borderRadius: 1,
        }}
      >
        <NetworkCheck sx={{ color: "secondary.main", fontSize: 32 }} />
        <Typography variant="h5" sx={{ fontWeight: 600 }}>
          SECTION 3: Three-Dimensional Multi-Metric Analysis
        </Typography>
      </Box>

      <Grid container spacing={3}>
        {/* Bubble Chart */}
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>
                Bubble Chart: Crashes (Y) vs. Latency (X), Size = Apdex, Color =
                Platform
              </Typography>
              <Box sx={{ height: 400 }}>
                <ResponsiveContainer>
                  <ScatterChart>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                    <XAxis
                      dataKey="latency"
                      name="Latency"
                      unit="ms"
                      type="number"
                    />
                    <YAxis dataKey="crashes" name="Crashes" type="number" />
                    <ZAxis dataKey="apdex" range={[100, 1000]} />
                    <Tooltip cursor={{ strokeDasharray: "3 3" }} />
                    <Legend />
                    <Scatter
                      name="Android"
                      data={bubbleData.filter((d) => d.platform === "Android")}
                      fill="#10b981"
                    />
                    <Scatter
                      name="iOS"
                      data={bubbleData.filter((d) => d.platform === "iOS")}
                      fill="#3b82f6"
                    />
                  </ScatterChart>
                </ResponsiveContainer>
              </Box>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ display: "block", mt: 1 }}
              >
                Bubble size represents Apdex Score. Labels show App Version.
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* Heatmap Table */}
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>
                Heatmap Table: Latency by OS Version × Device Model
              </Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell
                        sx={{ fontWeight: 600, bgcolor: "background.default" }}
                      >
                        OS Version
                      </TableCell>
                      <TableCell
                        align="center"
                        sx={{ fontWeight: 600, bgcolor: "background.default" }}
                      >
                        Samsung S23
                      </TableCell>
                      <TableCell
                        align="center"
                        sx={{ fontWeight: 600, bgcolor: "background.default" }}
                      >
                        Pixel 8
                      </TableCell>
                      <TableCell
                        align="center"
                        sx={{ fontWeight: 600, bgcolor: "background.default" }}
                      >
                        OnePlus 11
                      </TableCell>
                      <TableCell
                        align="center"
                        sx={{ fontWeight: 600, bgcolor: "background.default" }}
                      >
                        Samsung A54
                      </TableCell>
                      <TableCell
                        align="center"
                        sx={{ fontWeight: 600, bgcolor: "background.default" }}
                      >
                        iPhone 15
                      </TableCell>
                      <TableCell
                        align="center"
                        sx={{ fontWeight: 600, bgcolor: "background.default" }}
                      >
                        iPhone 14
                      </TableCell>
                      <TableCell
                        align="center"
                        sx={{ fontWeight: 600, bgcolor: "background.default" }}
                      >
                        iPhone 13
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {heatmapOSDevice.map((row) => (
                      <TableRow key={row.os}>
                        <TableCell sx={{ fontWeight: 600 }}>{row.os}</TableCell>
                        {Object.keys({
                          "Samsung S23": 1,
                          "Pixel 8": 1,
                          "OnePlus 11": 1,
                          "Samsung A54": 1,
                          "iPhone 15": 1,
                          "iPhone 14": 1,
                          "iPhone 13": 1,
                        }).map((device) => (
                          <TableCell
                            key={device}
                            align="center"
                            sx={{
                              bgcolor: row.devices[device]
                                ? getHeatmapColor(row.devices[device])
                                : "grey.200",
                              color: "white",
                              fontWeight: 600,
                            }}
                          >
                            {row.devices[device] || "-"}
                          </TableCell>
                        ))}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <Box
                sx={{
                  mt: 2,
                  display: "flex",
                  gap: 2,
                  alignItems: "center",
                  flexWrap: "wrap",
                }}
              >
                <Typography variant="caption" sx={{ fontWeight: 600 }}>
                  Color Scale (Latency ms):
                </Typography>
                <Chip
                  label="< 80ms - Excellent"
                  size="small"
                  sx={{ bgcolor: "#10b981", color: "white" }}
                />
                <Chip
                  label="80-100ms - Good"
                  size="small"
                  sx={{ bgcolor: "#84cc16", color: "white" }}
                />
                <Chip
                  label="100-130ms - Average"
                  size="small"
                  sx={{ bgcolor: "#f59e0b", color: "white" }}
                />
                <Chip
                  label="130-160ms - Poor"
                  size="small"
                  sx={{ bgcolor: "#ef4444", color: "white" }}
                />
                <Chip
                  label="> 160ms - Critical"
                  size="small"
                  sx={{ bgcolor: "#dc2626", color: "white" }}
                />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Comprehensive Table */}
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>
                Comprehensive Detailed Table: All Metrics × All Dimensions
                (Sorted by Crashes DESC)
              </Typography>
              <TableContainer sx={{ maxHeight: 600 }}>
                <Table stickyHeader size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell
                        sx={{ fontWeight: 600, bgcolor: "background.paper" }}
                      >
                        App Version
                      </TableCell>
                      <TableCell
                        sx={{ fontWeight: 600, bgcolor: "background.paper" }}
                      >
                        Device Model
                      </TableCell>
                      <TableCell
                        sx={{ fontWeight: 600, bgcolor: "background.paper" }}
                      >
                        OS Version
                      </TableCell>
                      <TableCell
                        sx={{ fontWeight: 600, bgcolor: "background.paper" }}
                      >
                        Location
                      </TableCell>
                      <TableCell
                        align="center"
                        sx={{ fontWeight: 600, bgcolor: "success.lighter" }}
                      >
                        Apdex
                      </TableCell>
                      <TableCell
                        align="center"
                        sx={{ fontWeight: 600, bgcolor: "error.lighter" }}
                      >
                        Error %
                      </TableCell>
                      <TableCell
                        align="center"
                        sx={{ fontWeight: 600, bgcolor: "info.lighter" }}
                      >
                        Latency
                      </TableCell>
                      <TableCell
                        align="center"
                        sx={{ fontWeight: 600, bgcolor: "error.lighter" }}
                      >
                        Crashes
                      </TableCell>
                      <TableCell
                        align="center"
                        sx={{ fontWeight: 600, bgcolor: "warning.lighter" }}
                      >
                        ANRs
                      </TableCell>
                      <TableCell
                        align="center"
                        sx={{ fontWeight: 600, bgcolor: "info.lighter" }}
                      >
                        Frozen
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {comprehensiveTableData.map((row, idx) => (
                      <TableRow key={idx} hover>
                        <TableCell>{row.appVersion}</TableCell>
                        <TableCell>{row.device}</TableCell>
                        <TableCell>{row.os}</TableCell>
                        <TableCell>{row.location}</TableCell>
                        <TableCell align="center">
                          <Chip
                            label={row.apdex}
                            size="small"
                            color={
                              row.apdex >= 0.95
                                ? "success"
                                : row.apdex >= 0.9
                                  ? "info"
                                  : "warning"
                            }
                          />
                        </TableCell>
                        <TableCell align="center">
                          <Chip
                            label={`${row.errorRate}%`}
                            size="small"
                            color={
                              row.errorRate < 1.5
                                ? "success"
                                : row.errorRate < 2.5
                                  ? "warning"
                                  : "error"
                            }
                          />
                        </TableCell>
                        <TableCell align="center">{row.latency}ms</TableCell>
                        <TableCell align="center">
                          <Chip
                            label={row.crashes}
                            size="small"
                            color={
                              row.crashes < 5
                                ? "success"
                                : row.crashes < 10
                                  ? "warning"
                                  : "error"
                            }
                          />
                        </TableCell>
                        <TableCell align="center">{row.anrs}</TableCell>
                        <TableCell align="center">{row.frozenFrames}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default ThreeDimensionalSection;
