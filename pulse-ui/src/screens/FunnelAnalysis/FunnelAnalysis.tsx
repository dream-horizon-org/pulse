import { useState } from "react";
import { Box, SegmentedControl, Select, Text } from "@mantine/core";
import { IconChartFunnel, IconRoute } from "@tabler/icons-react";
import classes from "./FunnelAnalysis.module.css";
import {
  GlobalFilterBar,
  ActiveFilter,
} from "./components/GlobalFilterBar";
import { FunnelBuilder, BuilderStep } from "./components/FunnelBuilder";
import { FunnelVisualization } from "./components/FunnelVisualization";
import { FunnelDataTable } from "./components/FunnelDataTable";
import { JourneyExplorer } from "./components/JourneyExplorer";
import { MOCK_FUNNEL_DATA, DATE_RANGE_OPTIONS } from "./mockData";

const INITIAL_STEPS: BuilderStep[] = [
  { id: "s-1", eventName: "Screen_View: Home" },
  { id: "s-2", eventName: "Screen_View: Product Detail" },
  { id: "s-3", eventName: "Tap: Add to Cart" },
  { id: "s-4", eventName: "Tap: Checkout" },
  { id: "s-5", eventName: "Tap: Place Order" },
];

export function FunnelAnalysis() {
  const [activeModule, setActiveModule] = useState<"funnels" | "journeys">(
    "funnels"
  );
  const [dateRange, setDateRange] = useState("7d");
  const [filters, setFilters] = useState<ActiveFilter[]>([]);

  // Funnel state
  const [steps, setSteps] = useState<BuilderStep[]>(INITIAL_STEPS);
  const [funnelMode, setFunnelMode] = useState<"ordered" | "unordered">(
    "ordered"
  );
  const [conversionWindow, setConversionWindow] = useState("86400");
  const [showResults, setShowResults] = useState(true);

  const handleAnalyze = () => {
    setShowResults(true);
  };

  return (
    <Box className={classes.shell}>
      {/* ===== Top Navigation Bar ===== */}
      <Box className={classes.topBar}>
        <Box className={classes.topBarLeft}>
          <SegmentedControl
            value={activeModule}
            onChange={(val) =>
              setActiveModule(val as "funnels" | "journeys")
            }
            data={[
              {
                label: (
                  <Box
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: 6,
                    }}
                  >
                    <IconChartFunnel size={15} />
                    <span>Funnels</span>
                  </Box>
                ),
                value: "funnels",
              },
              {
                label: (
                  <Box
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: 6,
                    }}
                  >
                    <IconRoute size={15} />
                    <span>Journeys</span>
                  </Box>
                ),
                value: "journeys",
              },
            ]}
            size="sm"
            color="teal"
          />
          <Text className={classes.moduleTitle}>
            {activeModule === "funnels"
              ? "Funnel Analysis"
              : "Journey Explorer"}
          </Text>
        </Box>

        <Box className={classes.topBarRight}>
          <Select
            data={DATE_RANGE_OPTIONS}
            value={dateRange}
            onChange={(val) => setDateRange(val || "7d")}
            size="xs"
            style={{ width: 160 }}
            allowDeselect={false}
          />
        </Box>
      </Box>

      {/* ===== Filter Bar ===== */}
      <GlobalFilterBar filters={filters} onFiltersChange={setFilters} />

      {/* ===== Module Content ===== */}
      {activeModule === "funnels" ? (
        <Box className={classes.funnelLayout}>
          {/* Left Sidebar: The Builder */}
          <Box className={classes.sidebar}>
            <FunnelBuilder
              steps={steps}
              onStepsChange={setSteps}
              funnelMode={funnelMode}
              onFunnelModeChange={setFunnelMode}
              conversionWindow={conversionWindow}
              onConversionWindowChange={setConversionWindow}
              onAnalyze={handleAnalyze}
            />
          </Box>

          {/* Right Canvas: The Visualization */}
          <Box className={classes.mainCanvas}>
            {showResults ? (
              <>
                <FunnelVisualization data={MOCK_FUNNEL_DATA} />
                <FunnelDataTable steps={MOCK_FUNNEL_DATA.steps} />
              </>
            ) : (
              <Box className={classes.emptyState}>
                <Box className={classes.emptyStateIcon}>
                  <IconChartFunnel size={28} color="#0ba09a" />
                </Box>
                <Text size="lg" fw={700} c="dark.6" mt="xs">
                  Build Your Funnel
                </Text>
                <Text size="sm" c="dimmed" mt={4} maw={380}>
                  Select events for each step in the builder, set your
                  conversion window, and click "Analyze Funnel" to see
                  results.
                </Text>
              </Box>
            )}
          </Box>
        </Box>
      ) : (
        <JourneyExplorer />
      )}
    </Box>
  );
}
