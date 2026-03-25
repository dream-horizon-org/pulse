import { useMemo } from "react";
import { Box, Select, Text, Button, ActionIcon, SegmentedControl, Tooltip, TextInput, Textarea } from "@mantine/core";
import { IconPlus, IconTrash, IconGripVertical } from "@tabler/icons-react";
import { CONVERSION_WINDOW_OPTIONS } from "../mockData";
import classes from "../FunnelAnalysis.module.css";

export interface BuilderStep {
  id: string;
  eventName: string;
}

interface FunnelBuilderProps {
  name: string;
  onNameChange: (name: string) => void;
  description: string;
  onDescriptionChange: (desc: string) => void;
  steps: BuilderStep[];
  onStepsChange: (steps: BuilderStep[]) => void;
  funnelMode: "ordered" | "unordered";
  onFunnelModeChange: (mode: "ordered" | "unordered") => void;
  conversionWindow: string;
  onConversionWindowChange: (value: string) => void;
  onAnalyze: () => void;
  isCreating: boolean;
  availableEvents: string[];
}

export function FunnelBuilder({
  name,
  onNameChange,
  description,
  onDescriptionChange,
  steps,
  onStepsChange,
  funnelMode,
  onFunnelModeChange,
  conversionWindow,
  onConversionWindowChange,
  onAnalyze,
  isCreating,
  availableEvents,
}: FunnelBuilderProps) {
  const eventOptions = useMemo(
    () => availableEvents.map((e) => ({ value: e, label: e })),
    [availableEvents],
  );

  const addStep = () => {
    onStepsChange([
      ...steps,
      { id: `step-${Date.now()}`, eventName: "" },
    ]);
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

  const hasValidSteps = steps.length >= 2 && steps.every((s) => s.eventName);
  const isValid = hasValidSteps && name.trim().length > 0;

  return (
    <Box className={classes.sidebarScroll}>
      <Text size="sm" fw={700} c="dark.7" mb="sm">
        Funnel Details
      </Text>
      <TextInput
        label="Name"
        placeholder="Enter funnel name"
        value={name}
        onChange={(e) => onNameChange(e.currentTarget.value)}
        size="xs"
        mb="sm"
        required
      />
      <Textarea
        label="Description"
        placeholder="Enter funnel description"
        value={description}
        onChange={(e) => onDescriptionChange(e.currentTarget.value)}
        size="xs"
        mb="md"
        minRows={2}
      />

      <Text size="sm" fw={700} c="dark.7" mb="sm">
        Funnel Builder
      </Text>
      <SegmentedControl
        value={funnelMode}
        onChange={(val) => onFunnelModeChange(val as "ordered" | "unordered")}
        data={[
          { label: "Sequential", value: "ordered" },
          { label: "Any Order", value: "unordered" },
        ]}
        size="xs"
        fullWidth
        color="teal"
        mb="md"
      />

      {steps.map((step, index) => (
        <Box key={step.id}>
          {index > 0 && (
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
              <IconGripVertical size={14} color="#cbd5e1" />
              <Tooltip
                label={steps.length <= 2 ? "Min 2 steps" : "Remove"}
              >
                <ActionIcon
                  variant="subtle"
                  color="red"
                  size="xs"
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
              placeholder={availableEvents.length === 0 ? "No events available" : "Select event..."}
              size="xs"
              searchable
              clearable
              disabled={availableEvents.length === 0}
            />
          </Box>
        </Box>
      ))}

      <Button
        variant="light"
        color="teal"
        size="xs"
        leftSection={<IconPlus size={14} />}
        onClick={addStep}
        className={classes.addStepBtn}
      >
        Add Step
      </Button>

      <Box className={classes.builderActions}>
        <Select
          label="Conversion window"
          data={CONVERSION_WINDOW_OPTIONS}
          value={conversionWindow}
          onChange={(val) => onConversionWindowChange(val || "86400")}
          size="xs"
          mb="sm"
          allowDeselect={false}
        />
        <Button
          fullWidth
          color="teal"
          size="sm"
          onClick={onAnalyze}
          disabled={!isValid || isCreating}
          loading={isCreating}
        >
          Create Funnel
        </Button>
      </Box>
    </Box>
  );
}
