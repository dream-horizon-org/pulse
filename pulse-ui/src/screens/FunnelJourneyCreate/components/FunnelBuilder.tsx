import { useMemo } from "react";
import {
  ActionIcon,
  Box,
  Button,
  Group,
  SegmentedControl,
  Select,
  TagsInput,
  Text,
  Textarea,
  TextInput,
  Tooltip,
} from "@mantine/core";
import { DateTimePicker } from "@mantine/dates";
import {
  IconGripVertical,
  IconInfoCircle,
  IconPlus,
  IconTrash,
} from "@tabler/icons-react";
import {
  DragDropContext,
  Draggable,
  Droppable,
  DropResult,
} from "@hello-pangea/dnd";
import { CRITICAL_INTERACTION_FORM_CONSTANTS } from "../../../constants";
import {
  CONVERSION_WINDOW_OPTIONS,
  DATE_RANGE_OPTIONS,
} from "../FunnelJourneyCreate.util";
import { useGetTags } from "../../../hooks/useGetFunnelData";
import classes from "../FunnelCreate.module.css";
import createFormClasses from "../FunnelJourneyCreateForm.module.css";

export interface BuilderStep {
  id: string;
  eventName: string;
}

interface FunnelBuilderProps {
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
  steps: BuilderStep[];
  onStepsChange: (steps: BuilderStep[]) => void;
  funnelMode: "ordered" | "unordered";
  onFunnelModeChange: (mode: "ordered" | "unordered") => void;
  conversionWindow: string;
  onConversionWindowChange: (value: string) => void;
  onAnalyze: () => void;
  isCreating: boolean;
  availableEvents: string[];
  isUpdateMode?: boolean;
  isValid?: boolean;
  /** Create flow: show only one wizard segment (0–2). Omit on funnel detail / full sidebar. */
  wizardStep?: 0 | 1 | 2;
}

export function FunnelBuilder({
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
  steps,
  onStepsChange,
  funnelMode,
  onFunnelModeChange,
  conversionWindow,
  onConversionWindowChange,
  onAnalyze,
  isCreating,
  availableEvents,
  isUpdateMode = false,
  isValid: externalIsValid,
  wizardStep,
}: FunnelBuilderProps) {
  const { data: tagsData } = useGetTags();
  const availableTags = tagsData?.data?.tags ?? [];

  const wiz = wizardStep !== undefined;
  const fieldSize = (
    wiz ? CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE : "xs"
  ) as "xs" | "sm" | "md";
  const fieldRadius = fieldSize;
  const accent: "blue" | "teal" = wiz ? "blue" : "teal";

  /** Include saved step values so detail/edit pre-fill works when API list omits an event. */
  const eventOptions = useMemo(() => {
    const names = new Set<string>();
    for (const e of availableEvents) {
      if (e?.trim()) names.add(e.trim());
    }
    for (const s of steps) {
      if (s.eventName?.trim()) names.add(s.eventName.trim());
    }
    return Array.from(names)
      .sort((a, b) => a.localeCompare(b))
      .map((e) => ({ value: e, label: e }));
  }, [availableEvents, steps]);

  const addStep = () => {
    onStepsChange([...steps, { id: `step-${Date.now()}`, eventName: "" }]);
  };

  const removeStep = (index: number) => {
    if (steps.length <= 2) return;
    onStepsChange(steps.filter((_, i) => i !== index));
  };

  const updateStep = (index: number, eventName: string) => {
    const updated = [...steps];
    updated[index] = { ...updated[index], eventName };
    onStepsChange(updated);
  };

  const onDragEnd = (result: DropResult) => {
    if (!result.destination) return;
    const sourceIndex = result.source.index;
    const destinationIndex = result.destination.index;
    if (sourceIndex === destinationIndex) return;

    const newSteps = Array.from(steps);
    const [reorderedStep] = newSteps.splice(sourceIndex, 1);
    newSteps.splice(destinationIndex, 0, reorderedStep);
    onStepsChange(newSteps);
  };

  const hasValidSteps = steps.length >= 2 && steps.every((s) => s.eventName);
  const isValid =
    externalIsValid !== undefined
      ? externalIsValid
      : hasValidSteps && name.trim().length > 0;

  const rollingScheduleSection = (
    <Box mt={wizardStep !== undefined ? 0 : "xl"}>
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
                Funnel will be auto-updated every 24 hours for the specified
                rolling window.
              </Text>
              <Text size="xs" fw={600} mb={4}>
                Once
              </Text>
              <Text size="xs">
                Funnel will be computed once after creation and will not
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
        size={fieldSize}
        fullWidth
        color={accent}
        mb="sm"
      />

      {rollingType === "ONCE" && (
        <Group gap="xs" mb="md">
          <DateTimePicker
            placeholder="Start Date"
            value={customStartDate}
            onChange={onCustomStartDateChange}
            size={fieldSize}
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
            size={fieldSize}
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
            size={fieldSize}
            mb="sm"
            allowDeselect={false}
          />
          <Group gap="xs" mb={4}>
            <Text size="sm" fw={500}>
              Expiry Date
            </Text>
            <Tooltip
              label="The date when this funnel will stop auto-updating and be marked as completed."
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
            clearable
          />
        </>
      )}
    </Box>
  );

  const tagsSection = (
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
        mb="md"
        clearable
      />
    </>
  );

  const stepsAndDragSection = (
    <>
      <Box mt={wizardStep !== undefined ? 0 : "lg"}>
        <Text className={createFormClasses.formStepTitle} component="div">
          Funnel Builder
        </Text>
      </Box>
      <SegmentedControl
        value={funnelMode}
        onChange={(val) => onFunnelModeChange(val as "ordered" | "unordered")}
        data={[
          { label: "Sequential", value: "ordered" },
          { label: "Any Order", value: "unordered" },
        ]}
        size={fieldSize}
        fullWidth
        color={accent}
        mb="md"
      />

      <DragDropContext onDragEnd={onDragEnd}>
        <Droppable
          droppableId="funnel-steps"
          isDropDisabled={funnelMode === "ordered"}
        >
          {(provided) => (
            <Box ref={provided.innerRef} {...provided.droppableProps}>
              {steps.map((step, index) => (
                <Draggable
                  key={step.id}
                  draggableId={step.id}
                  index={index}
                  isDragDisabled={funnelMode === "ordered"}
                >
                  {(providedDraggable, snapshot) => (
                    <Box
                      ref={providedDraggable.innerRef}
                      {...providedDraggable.draggableProps}
                      style={{
                        ...providedDraggable.draggableProps.style,
                        opacity: snapshot.isDragging ? 0.8 : 1,
                      }}
                    >
                      {index > 0 && !snapshot.isDragging && (
                        <Box className={classes.stepConnector}>
                          <Box className={classes.connectorLine} />
                        </Box>
                      )}
                      <Box className={classes.stepBlock}>
                        <Box className={classes.stepBlockHeader}>
                          <Box className={classes.stepNumber}>{index + 1}</Box>
                          <Text className={classes.stepLabel}>
                            Step {index + 1}
                          </Text>
                          <Box style={{ flex: 1 }} />
                          <Box
                            {...providedDraggable.dragHandleProps}
                            style={{
                              display: "flex",
                              alignItems: "center",
                              cursor:
                                funnelMode === "ordered"
                                  ? "not-allowed"
                                  : "grab",
                            }}
                          >
                            <IconGripVertical size={14} color="#cbd5e1" />
                          </Box>
                          <Tooltip
                            label={steps.length <= 2 ? "Min 2 steps" : "Remove"}
                          >
                            <ActionIcon
                              variant="subtle"
                              color="red"
                              size={wiz ? "sm" : "xs"}
                              onClick={() => removeStep(index)}
                              disabled={steps.length <= 2}
                            >
                              <IconTrash size={13} />
                            </ActionIcon>
                          </Tooltip>
                        </Box>
                        <Select
                          data={eventOptions}
                          value={step.eventName || null}
                          onChange={(val) => updateStep(index, val || "")}
                          placeholder={
                            availableEvents.length === 0
                              ? "No events available"
                              : "Select event..."
                          }
                          size={fieldSize}
                          searchable
                          clearable
                          disabled={availableEvents.length === 0}
                        />
                      </Box>
                    </Box>
                  )}
                </Draggable>
              ))}
              {provided.placeholder}
            </Box>
          )}
        </Droppable>
      </DragDropContext>

      <Button
        variant="light"
        color={accent}
        size={fieldSize}
        leftSection={<IconPlus size={14} />}
        onClick={addStep}
        className={classes.addStepBtn}
      >
        Add Step
      </Button>
    </>
  );

  const conversionWindowFields = (
    <Box
      className={wiz ? undefined : classes.builderActions}
      mt={wiz ? "lg" : 0}
      pt={wiz ? 0 : undefined}
      style={wiz ? { borderTop: "none" } : undefined}
    >
      <Text size="sm" fw={500} mb={4}>
        Conversion Window
      </Text>
      <Select
        data={CONVERSION_WINDOW_OPTIONS}
        value={conversionWindow}
        onChange={(val) => onConversionWindowChange(val || "86400")}
        size={fieldSize}
        mb="sm"
        allowDeselect={false}
      />
      {wizardStep === undefined && (
        <Button
          fullWidth
          color="teal"
          size="sm"
          onClick={onAnalyze}
          disabled={!isValid || isCreating}
          loading={isCreating}
        >
          {isCreating
            ? isUpdateMode
              ? "Updating..."
              : "Creating..."
            : isUpdateMode
              ? "Update Funnel"
              : "Create Funnel"}
        </Button>
      )}
    </Box>
  );

  if (wizardStep === 0) {
    return (
      <Box w="100%">
        <Text className={createFormClasses.formStepTitle} component="div">
          Funnel basics
        </Text>
        <Text size="sm" fw={500} mb={4}>
          Name
        </Text>
        <TextInput
          placeholder="Enter funnel name"
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
          placeholder="Enter funnel description"
          value={description}
          onChange={(e) => onDescriptionChange(e.currentTarget.value)}
          size={fieldSize}
          radius={fieldRadius}
          mb="md"
          minRows={2}
        />
        {tagsSection}
      </Box>
    );
  }

  if (wizardStep === 1) {
    return (
      <Box w="100%">
        <Text className={createFormClasses.formStepTitle} component="div">
          Time window
        </Text>
        {rollingScheduleSection}
      </Box>
    );
  }

  if (wizardStep === 2) {
    return (
      <Box w="100%">
        {stepsAndDragSection}
        {conversionWindowFields}
      </Box>
    );
  }

  return (
    <Box className={classes.sidebarScroll}>
      <Text className={createFormClasses.formStepTitle} component="div">
        Funnel Details
      </Text>
      <Text size="sm" fw={500} mb={4}>
        Name
      </Text>
      <TextInput
        placeholder="Enter funnel name"
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
        placeholder="Enter funnel description"
        value={description}
        onChange={(e) => onDescriptionChange(e.currentTarget.value)}
        size="xs"
        mb="md"
        minRows={2}
      />

      {stepsAndDragSection}

      {rollingScheduleSection}

      {tagsSection}

      {conversionWindowFields}
    </Box>
  );
}
