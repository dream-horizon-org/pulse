import { useMemo, useState } from "react";
import {
  Box,
  Loader,
  SegmentedControl,
  Select,
  Slider,
  Text,
  Group,
  TextInput,
  Textarea,
  Button,
} from "@mantine/core";
import ReactECharts from "echarts-for-react";
import { useGetJourneyData } from "../../../hooks/useGetFunnelData";
import { getDateRangeFromPreset } from "../mockData";
import { buildJourneySankeyOption } from "../utils/buildJourneySankeyOption";
import classes from "../FunnelAnalysis.module.css";

interface JourneyExplorerProps {
  name: string;
  onNameChange: (name: string) => void;
  description: string;
  onDescriptionChange: (desc: string) => void;
  dateRange: string;
  availableEvents: string[];
  onCreate: (config: any) => void;
  isCreating: boolean;
}

export function JourneyExplorer({
  name,
  onNameChange,
  description,
  onDescriptionChange,
  dateRange,
  availableEvents,
  onCreate,
  isCreating,
}: JourneyExplorerProps) {
  const [direction, setDirection] = useState<"forward" | "reverse">("forward");
  const [anchorEvent, setAnchorEvent] = useState<string | null>(null);
  const [depth, setDepth] = useState(5);

  const eventOptions = useMemo(
    () => availableEvents.map((e) => ({ value: e, label: e })),
    [availableEvents],
  );

  const timeRange = useMemo(() => getDateRangeFromPreset(dateRange), [dateRange]);

  const requestBody = useMemo(
    () => ({
      direction,
      anchorEvent: anchorEvent || "",
      depth,
      timeRange,
    }),
    [direction, anchorEvent, depth, timeRange],
  );

  const { data, isLoading } = useGetJourneyData({
    requestBody,
    enabled: !!anchorEvent,
  });

  const journeyData = data?.data;
  const isValid = name.trim().length > 0 && !!anchorEvent;

  const handleCreate = () => {
    onCreate({
      direction,
      anchorEvent,
      depth,
    });
  };

  return (
    <Box className={classes.journeyLayout}>
      <Box className={classes.sidebarScroll} style={{ width: 300, borderRight: "1px solid #e2e8f0", padding: 16 }}>
        <Text size="sm" fw={700} c="dark.7" mb="sm">
          Journey Details
        </Text>
        <TextInput
          label="Name"
          placeholder="Enter journey name"
          value={name}
          onChange={(e) => onNameChange(e.currentTarget.value)}
          size="xs"
          mb="sm"
          required
        />
        <Textarea
          label="Description"
          placeholder="Enter journey description"
          value={description}
          onChange={(e) => onDescriptionChange(e.currentTarget.value)}
          size="xs"
          mb="xl"
          minRows={2}
        />

        <Text size="sm" fw={700} c="dark.7" mb="sm">
          Journey Configuration
        </Text>
        <Select
          label="Anchor Event"
          data={eventOptions}
          value={anchorEvent}
          onChange={setAnchorEvent}
          placeholder={availableEvents.length === 0 ? "No events available" : "Select root event..."}
          size="xs"
          searchable
          mb="sm"
          disabled={availableEvents.length === 0}
          required
        />

        <Text size="xs" fw={600} c="dark.7" mb={4}>Direction</Text>
        <SegmentedControl
          value={direction}
          onChange={(val) => setDirection(val as "forward" | "reverse")}
          data={[
            { label: "Start Point →", value: "forward" },
            { label: "← End Point", value: "reverse" },
          ]}
          size="xs"
          color="teal"
          fullWidth
          mb="sm"
        />

        <Text size="xs" fw={600} c="dark.7" mb={4}>Depth: {depth} steps</Text>
        <Slider
          value={depth}
          onChange={setDepth}
          min={1}
          max={10}
          step={1}
          marks={[
            { value: 1, label: "1" },
            { value: 5, label: "5" },
            { value: 10, label: "10" },
          ]}
          size="sm"
          color="teal"
          style={{ marginTop: 4, marginBottom: 32 }}
        />

        <Button
          fullWidth
          color="teal"
          size="sm"
          onClick={handleCreate}
          disabled={!isValid || isCreating}
          loading={isCreating}
        >
          Create Journey
        </Button>
      </Box>

      <Box className={classes.journeyCanvas}>
        <Box className={classes.sankeyContainer}>
          {anchorEvent ? (
            <Text size="sm" fw={600} c="dark.7" mb="md">
              {direction === "forward" ? "Forward" : "Reverse"} Journey from{" "}
              <Text span c="teal" fw={700}>{anchorEvent}</Text>
            </Text>
          ) : null}

          {isLoading ? (
            <Box style={{ display: "flex", justifyContent: "center", padding: 80 }}>
              <Loader color="teal" size="lg" />
            </Box>
          ) : journeyData ? (
            <ReactECharts
              option={buildJourneySankeyOption(journeyData)}
              style={{ height: "520px", width: "100%" }}
              notMerge
            />
          ) : (
            <Box className={classes.emptyState}>
              <Text size="sm" c="dimmed">
                {availableEvents.length === 0
                  ? "No events available. Connect a data source to explore journeys."
                  : "Select an anchor event to explore journeys"}
              </Text>
            </Box>
          )}
        </Box>
      </Box>
    </Box>
  );
}
