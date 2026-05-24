import { useMemo, useState } from "react";
import {
  Box,
  Button,
  Group,
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
import { IconInfoCircle, IconRoute } from "@tabler/icons-react";
import { useGetTags } from "../../../hooks/useGetFunnelData";
import { CRITICAL_INTERACTION_FORM_CONSTANTS } from "../../../constants";
import { DATE_RANGE_OPTIONS } from "../FunnelJourneyCreate.util";
import classes from "../FunnelCreate.module.css";
import createFormClasses from "../FunnelJourneyCreateForm.module.css";
import { AnalysisBasisChoice } from "./AnalysisBasisChoice";
import { FunnelType, type AnalysisBasis } from "../../../services/funnels.service";

interface JourneyExplorerProps {
  name: string;
  onNameChange: (name: string) => void;
  description: string;
  onDescriptionChange: (desc: string) => void;
  tags: string[];
  onTagsChange: (tags: string[]) => void;
  rollingType: FunnelType;
  onRollingTypeChange: (type: FunnelType) => void;
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
  anchorEvent?: string;
  onAnchorEventChange?: (event: string) => void;
  direction?: "START" | "END";
  onDirectionChange?: (direction: "START" | "END") => void;
  depth?: number;
  onDepthChange?: (depth: number) => void;
  analysisBasis?: AnalysisBasis;
  onAnalysisBasisChange?: (basis: AnalysisBasis) => void;
  /** Create wizard: show one segment only (0–2). Omit on journey detail. */
  wizardStep?: 0 | 1 | 2;
  /**
   * When true (create wizard), path fields use props + callbacks like update mode
   * so the parent can submit from the final step without keeping this component mounted.
   */
  useExternalPathState?: boolean;
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
  anchorEvent: propAnchorEvent,
  onAnchorEventChange,
  direction: propDirection,
  onDirectionChange,
  depth: propDepth,
  onDepthChange,
  analysisBasis: propAnalysisBasis,
  onAnalysisBasisChange,
  wizardStep,
  useExternalPathState = false,
}: JourneyExplorerProps) {
  const [localDirection, setLocalDirection] = useState<"START" | "END">(
    "START",
  );
  const [localAnchorEvent, setLocalAnchorEvent] = useState<string | null>(null);
  const [localDepth, setLocalDepth] = useState(5);
  const [localAnalysisBasis, setLocalAnalysisBasis] =
    useState<AnalysisBasis>("EVENT");

  const pathFromProps = isUpdateMode || useExternalPathState;

  const direction = pathFromProps ? propDirection || "START" : localDirection;
  const anchorEvent = pathFromProps
    ? propAnchorEvent || null
    : localAnchorEvent;
  // Clamp to the slider's [1, 10] domain so any pre-existing journey saved
  // when the cap was 15 still loads cleanly into the picker.
  const depth = Math.min(
    10,
    Math.max(1, pathFromProps ? (propDepth ?? 5) : localDepth),
  );
  const analysisBasis = pathFromProps
    ? propAnalysisBasis ?? "EVENT"
    : localAnalysisBasis;

  const setDirection = (dir: "START" | "END") => {
    if (pathFromProps && onDirectionChange) {
      onDirectionChange(dir);
    } else {
      setLocalDirection(dir);
    }
  };

  const setAnchorEvent = (event: string | null) => {
    if (pathFromProps && onAnchorEventChange) {
      onAnchorEventChange(event || "");
    } else {
      setLocalAnchorEvent(event);
    }
  };

  const setDepth = (newDepth: number) => {
    if (pathFromProps && onDepthChange) {
      onDepthChange(newDepth);
    } else {
      setLocalDepth(newDepth);
    }
  };

  const setAnalysisBasis = (basis: AnalysisBasis) => {
    if (pathFromProps && onAnalysisBasisChange) {
      onAnalysisBasisChange(basis);
    } else {
      setLocalAnalysisBasis(basis);
    }
  };

  const { data: tagsData } = useGetTags();
  const availableTags = tagsData?.data?.tags ?? [];

  const wiz = wizardStep !== undefined;
  const fieldSize = (
    wiz ? CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE : "xs"
  ) as "xs" | "sm" | "md";
  const fieldRadius = fieldSize;
  const accent: "blue" | "teal" = wiz ? "blue" : "teal";

  const eventOptions = useMemo(() => {
    const names = new Set<string>();
    for (const e of availableEvents) {
      if (e?.trim()) names.add(e.trim());
    }
    if (anchorEvent?.trim()) names.add(anchorEvent.trim());
    return Array.from(names)
      .sort((a, b) => a.localeCompare(b))
      .map((e) => ({ value: e, label: e }));
  }, [availableEvents, anchorEvent]);

  const apiFilters = useMemo(() => {
    const grouped: Record<string, string[]> = {};
    for (const f of filters) {
      (grouped[f.property] ??= []).push(f.value);
    }
    return Object.entries(grouped).map(([field, values]) => ({
      field,
      operator: "EQ" as const,
      value: values,
    }));
  }, [filters]);

  const isValid =
    externalIsValid !== undefined
      ? externalIsValid
      : name.trim().length > 0 && !!anchorEvent;

  const handleCreate = () => {
    onCreate({
      direction,
      anchorEvent,
      depth,
      analysisBasis,
      filters: apiFilters,
    });
  };

  const showCreateButton =
    wizardStep === undefined && (isUpdateMode || !useExternalPathState);

  const rollingBlock = (
    <Box mt={wizardStep !== undefined ? 0 : 0}>
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
        onChange={(val) => onRollingTypeChange(val as FunnelType)}
        data={[
          { label: "Auto", value: FunnelType.AUTO },
          { label: "Once", value: FunnelType.ONCE },
        ]}
        size={fieldSize}
        fullWidth
        color={accent}
        mb="sm"
      />

      {rollingType === FunnelType.ONCE && (
        <Box mb="md">
          <DateTimePicker
            label="Start Date"
            placeholder="Start Date"
            value={customStartDate}
            onChange={onCustomStartDateChange}
            size={fieldSize}
            clearable
            mb="xs"
          />
          <DateTimePicker
            label="End Date"
            placeholder="End Date"
            value={customEndDate}
            onChange={onCustomEndDateChange}
            size={fieldSize}
            clearable
          />
        </Box>
      )}
      {rollingType === FunnelType.AUTO && (
        <>
          <Select
            data={DATE_RANGE_OPTIONS.filter((opt) => opt.value !== "custom")}
            value={dateRange}
            onChange={(val) => onDateRangeChange(val || "7d")}
            size={fieldSize}
            mb="sm"
            allowDeselect={false}
          />
          <Group gap="xs" mb={4}>
            <Text size="sm" fw={500}>
              Expiry Date <Text component="span" c="red">*</Text>
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
            placeholder="Select expiry date"
            value={expiryDate}
            onChange={onExpiryDateChange}
            size={fieldSize}
            mb="md"
            required
          />
        </>
      )}
    </Box>
  );

  const tagsBlock = (
    <>
      <Text size="sm" fw={500} mb={4}>
        Tags
      </Text>
      <TagsInput
        placeholder="Select or create tags"
        data={availableTags}
        value={tags}
        onChange={onTagsChange}
        size={fieldSize}
        mb="xl"
        clearable
      />
    </>
  );

  const pathBlock = (
    <>
      <Box mt={wizardStep !== undefined ? 0 : "lg"}>
        <Text className={createFormClasses.formStepTitle} component="div">
          Journey Configuration
        </Text>
      </Box>
      <Text size="sm" fw={500} mb={4}>
        Anchor Event
      </Text>
      <Select
        data={eventOptions}
        value={anchorEvent || null}
        onChange={setAnchorEvent}
        placeholder={
          availableEvents.length === 0
            ? "No events available"
            : "Select root event..."
        }
        size={fieldSize}
        searchable
        mb="sm"
        disabled={availableEvents.length === 0}
        required
      />

      <AnalysisBasisChoice
        value={analysisBasis}
        onChange={setAnalysisBasis}
        variant="journey"
        disabled={isUpdateMode}
        accent={accent}
        size={fieldSize}
      />

      <Text size="sm" fw={500} mt="md" mb={4}>
        Show path from anchor
      </Text>
      <SegmentedControl
        value={direction}
        onChange={(val) => setDirection(val as "START" | "END")}
        data={[
          { label: "Start Point →", value: "START" },
          { label: "← End Point", value: "END" },
        ]}
        size={fieldSize}
        color={accent}
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
        max={10}
        step={1}
        marks={[
          { value: 1, label: "1" },
          { value: 5, label: "5" },
          { value: 10, label: "10" },
        ]}
        size={wiz ? "md" : "sm"}
        color={accent}
        style={{ marginTop: 4, marginBottom: wizardStep === 2 ? 8 : 32 }}
      />
    </>
  );

  if (wizardStep === 0) {
    return (
      <Box w="100%">
        <Text className={createFormClasses.formStepTitle} component="div">
          Journey basics
        </Text>
        <Text size="sm" fw={500} mb={4}>
          Name
        </Text>
        <TextInput
          placeholder="Enter journey name"
          value={name}
          onChange={(e) => onNameChange(e.currentTarget.value)}
          size={fieldSize}
          radius={fieldRadius}
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
          size={fieldSize}
          radius={fieldRadius}
          mb="md"
          minRows={2}
        />
        {tagsBlock}
      </Box>
    );
  }

  if (wizardStep === 1) {
    return (
      <Box w="100%">
        <Text className={createFormClasses.formStepTitle} component="div">
          Time window
        </Text>
        {rollingBlock}
      </Box>
    );
  }

  if (wizardStep === 2) {
    return <Box w="100%">{pathBlock}</Box>;
  }

  return (
    <Box className={classes.journeyLayout}>
      <Box
        className={classes.sidebarScroll}
        style={{
          width: isUpdateMode ? "100%" : 300,
          borderRight: isUpdateMode ? "none" : "1px solid #e2e8f0",
          padding: 16,
        }}
      >
        <Text className={createFormClasses.formStepTitle} component="div">
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

        {rollingBlock}

        {tagsBlock}

        {pathBlock}

        {showCreateButton && (
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
        )}
      </Box>

      {!isUpdateMode && (
        <Box
          className={classes.mainCanvas}
          style={{
            flex: 1,
            minWidth: 0,
            display: "flex",
            flexDirection: "column",
          }}
        >
          <Box
            className={classes.emptyState}
            style={{ flex: 1, justifyContent: "center" }}
          >
            <Box className={classes.emptyStateIcon}>
              <IconRoute size={28} color="#0ba09a" />
            </Box>
            <Text size="lg" fw={700} c="dark.6" mt="xs">
              Build Your Journey
            </Text>
            <Text size="sm" c="dimmed" mt={4} maw={380}>
              Choose an anchor event, set direction and depth, configure your
              rolling window and filters, then click &quot;Create Journey&quot;
              to save. Visualization appears on the journey detail page after
              creation.
            </Text>
          </Box>
        </Box>
      )}
    </Box>
  );
}
