import { useMemo, useState } from "react";
import {
  Box,
  Button,
  Group,
  Loader,
  SegmentedControl,
  Select,
  Slider,
  TagsInput,
  Text,
  Textarea,
  TextInput,
  Tooltip,
} from "@mantine/core";
import { DateTimePicker } from "@mantine/dates";
import { IconInfoCircle } from "@tabler/icons-react";
import ReactECharts from "echarts-for-react";
import { useGetJourneyData, useGetTags } from "../../../hooks/useGetFunnelData";
import { DATE_RANGE_OPTIONS, getDateRangeFromPreset } from "../mockData";
import { buildJourneySankeyOption } from "../utils/buildJourneySankeyOption";
import classes from "../FunnelAnalysis.module.css";

interface JourneyExplorerProps {
  name: string;
  onNameChange: (name: string) => void;
  description: string;
  onDescriptionChange: (desc: string) => void;
  tags: string[];
  onTagsChange: (tags: string[]) => void;
  rollingType: "RECURRING" | "ONCE";
  onRollingTypeChange: (type: "RECURRING" | "ONCE") => void;
  dateRange: string;
  onDateRangeChange: (range: string) => void;
  customStartDate: Date | null;
  onCustomStartDateChange: (date: Date | null) => void;
  customEndDate: Date | null;
  onCustomEndDateChange: (date: Date | null) => void;
  expiryDate: Date | null;
  onExpiryDateChange: (date: Date | null) => void;
  availableEvents: string[];
  onCreate: (config: any) => void;
  isCreating: boolean;
  filters: { property: string; value: string }[];
  isUpdateMode?: boolean;
  isValid?: boolean;
}

export function JourneyExplorer({
  name,
  onNameChange,
  description,
  onDescriptionChange,
  tags,
  onTagsChange,
  rollingType,
  onRollingTypeChange,
  dateRange,
  onDateRangeChange,
  customStartDate,
  onCustomStartDateChange,
  customEndDate,
  onCustomEndDateChange,
  expiryDate,
  onExpiryDateChange,
  availableEvents,
  onCreate,
  isCreating,
  filters,
  isUpdateMode = false,
  isValid: externalIsValid,
}: JourneyExplorerProps) {
  const [direction, setDirection] = useState<"forward" | "reverse">("forward");
  const [anchorEvent, setAnchorEvent] = useState<string | null>(null);
  const [depth, setDepth] = useState(5);

  const { data: tagsData } = useGetTags();
  const availableTags = tagsData?.data?.tags ?? [];

  const eventOptions = useMemo(
    () => availableEvents.map((e) => ({ value: e, label: e })),
    [availableEvents],
  );

  const timeRange = useMemo(() => {
    if (rollingType === "ONCE") {
      return {
        start: customStartDate
          ? customStartDate.toISOString()
          : new Date().toISOString(),
        end: customEndDate
          ? customEndDate.toISOString()
          : new Date().toISOString(),
      };
    }
    return getDateRangeFromPreset(dateRange);
  }, [rollingType, dateRange, customStartDate, customEndDate]);

  const apiFilters = useMemo(
    () =>
      filters.map((f) => ({
        field: f.property,
        operator: "EQ" as const,
        value: f.value,
      })),
    [filters],
  );

  const requestBody = useMemo(
    () => ({
      direction,
      anchorEvent: anchorEvent || "",
      depth,
      timeRange,
      filters: apiFilters,
    }),
    [direction, anchorEvent, depth, timeRange, apiFilters],
  );

  const { data, isLoading } = useGetJourneyData({
    requestBody,
    enabled: !!anchorEvent,
  });

  const journeyData = data?.data;
  const isValid =
    externalIsValid !== undefined
      ? externalIsValid
      : name.trim().length > 0 && !!anchorEvent;

  const handleCreate = () => {
    onCreate({
      direction,
      anchorEvent,
      depth,
      timeRange,
      filters: apiFilters,
    });
  };

  return (
    <Box className={classes.journeyLayout}>
      <Box
        className={classes.sidebarScroll}
        style={{ width: 300, borderRight: "1px solid #e2e8f0", padding: 16 }}
      >
        <Text size="md" fw={600} mb="sm">
          Journey Details
        </Text>
        <Text size="sm" fw={500} mb={4}>
          Name
        </Text>
        <TextInput
          placeholder="Enter journey name"
          value={name}
          onChange={(e) => onNameChange(e.currentTarget.value)}
          size="xs"
          mb="sm"
          required
        />
        <Text size="sm" fw={500} mb={4}>
          Description
        </Text>
        <Textarea
          placeholder="Enter journey description"
          value={description}
          onChange={(e) => onDescriptionChange(e.currentTarget.value)}
          size="xs"
          mb="xl"
          minRows={2}
        />

        <Group gap="xs" mb="sm">
          <Text size="sm" fw={500}>
            Rolling Type
          </Text>
          <Tooltip
            label={
              <Box w={200}>
                <Text size="xs" fw={600} mb={4}>
                  Recurring
                </Text>
                <Text size="xs" mb={8}>
                  Journey will be auto-updated every 24 hours for the specified
                  rolling window.
                </Text>
                <Text size="xs" fw={600} mb={4}>
                  Once
                </Text>
                <Text size="xs">
                  Journey will be computed once after creation and will not
                  auto-update.
                </Text>
              </Box>
            }
            position="right"
            withArrow
            multiline
          >
            <IconInfoCircle
              size={14}
              color="#94a3b8"
              style={{ cursor: "help" }}
            />
          </Tooltip>
        </Group>
        <SegmentedControl
          value={rollingType}
          onChange={(val) => onRollingTypeChange(val as "RECURRING" | "ONCE")}
          data={[
            { label: "Recurring", value: "RECURRING" },
            { label: "Once", value: "ONCE" },
          ]}
          size="xs"
          fullWidth
          color="teal"
          mb="sm"
        />

        {rollingType === "ONCE" && (
          <Group gap="xs" mb="md">
            <DateTimePicker
              placeholder="Start Date"
              value={customStartDate}
              onChange={onCustomStartDateChange}
              size="xs"
              style={{ flex: 1 }}
              clearable
            />
            <Text size="xs" c="dimmed">
              -
            </Text>
            <DateTimePicker
              placeholder="End Date"
              value={customEndDate}
              onChange={onCustomEndDateChange}
              size="xs"
              style={{ flex: 1 }}
              clearable
            />
          </Group>
        )}
        {rollingType === "RECURRING" && (
          <>
            <Select
              data={DATE_RANGE_OPTIONS.filter((opt) => opt.value !== "custom")}
              value={dateRange}
              onChange={(val) => onDateRangeChange(val || "7d")}
              size="xs"
              mb="sm"
              allowDeselect={false}
            />
            <Group gap="xs" mb={4}>
              <Text size="sm" fw={500}>
                Expiry Date
              </Text>
              <Tooltip
                label="The date when this journey will stop auto-updating and be marked as completed."
                position="right"
                withArrow
              >
                <IconInfoCircle
                  size={14}
                  color="#94a3b8"
                  style={{ cursor: "help" }}
                />
              </Tooltip>
            </Group>
            <DateTimePicker
              placeholder="Select expiry date (optional)"
              value={expiryDate}
              onChange={onExpiryDateChange}
              size="xs"
              mb="md"
              clearable
            />
          </>
        )}

        <Text size="sm" fw={500} mb={4}>
          Tags
        </Text>
        <TagsInput
          placeholder="Select or create tags"
          data={availableTags}
          value={tags}
          onChange={onTagsChange}
          size="xs"
          mb="xl"
          clearable
        />

        <Text size="md" fw={600} mt="lg" mb="sm">
          Journey Configuration
        </Text>
        <Text size="sm" fw={500} mb={4}>
          Anchor Event
        </Text>
        <Select
          data={eventOptions}
          value={anchorEvent}
          onChange={setAnchorEvent}
          placeholder={
            availableEvents.length === 0
              ? "No events available"
              : "Select root event..."
          }
          size="xs"
          searchable
          mb="sm"
          disabled={availableEvents.length === 0}
          required
        />

        <Text size="sm" fw={500} mt="md" mb={4}>
          Direction
        </Text>
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

        <Text size="sm" fw={500} mt="md" mb={4}>
          Depth: {depth} steps
        </Text>
        <Slider
          value={depth}
          onChange={setDepth}
          min={1}
          max={15}
          step={1}
          marks={[
            { value: 1, label: "1" },
            { value: 5, label: "5" },
            { value: 10, label: "10" },
            { value: 15, label: "15" },
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
          {isCreating
            ? isUpdateMode
              ? "Updating..."
              : "Creating..."
            : isUpdateMode
              ? "Update Journey"
              : "Create Journey"}
        </Button>
      </Box>

      <Box className={classes.journeyCanvas}>
        <Box className={classes.sankeyContainer}>
          {anchorEvent ? (
            <Text size="md" fw={600} mb="md">
              {direction === "forward" ? "Forward" : "Reverse"} Journey from{" "}
              <Text span c="teal" fw={700}>
                {anchorEvent}
              </Text>
            </Text>
          ) : null}

          {isLoading ? (
            <Box
              style={{ display: "flex", justifyContent: "center", padding: 80 }}
            >
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
