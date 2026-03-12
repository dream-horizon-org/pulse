import { Box, Select, Text, Button, ActionIcon, SegmentedControl, Tooltip } from "@mantine/core";
import { IconPlus, IconTrash, IconGripVertical } from "@tabler/icons-react";
import { AVAILABLE_EVENTS, CONVERSION_WINDOW_OPTIONS } from "../mockData";
import classes from "../FunnelAnalysis.module.css";

export interface BuilderStep {
  id: string;
  eventName: string;
}

interface FunnelBuilderProps {
  steps: BuilderStep[];
  onStepsChange: (steps: BuilderStep[]) => void;
  funnelMode: "ordered" | "unordered";
  onFunnelModeChange: (mode: "ordered" | "unordered") => void;
  conversionWindow: string;
  onConversionWindowChange: (value: string) => void;
  onAnalyze: () => void;
}

const eventOptions = AVAILABLE_EVENTS.map((e) => ({ value: e, label: e }));

export function FunnelBuilder({
  steps,
  onStepsChange,
  funnelMode,
  onFunnelModeChange,
  conversionWindow,
  onConversionWindowChange,
  onAnalyze,
}: FunnelBuilderProps) {
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

  return (
    <>
      <Box className={classes.sidebarHeader}>
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
        />
      </Box>

      <Box className={classes.sidebarContent}>
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
                placeholder="Select event..."
                size="xs"
                searchable
                clearable
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
      </Box>

      <Box className={classes.sidebarFooter}>
        <Select
          label="Conversion window"
          data={CONVERSION_WINDOW_OPTIONS}
          value={conversionWindow}
          onChange={(val) => onConversionWindowChange(val || "86400")}
          size="xs"
          mb="md"
          allowDeselect={false}
        />
        <Button
          fullWidth
          color="teal"
          size="sm"
          onClick={onAnalyze}
          disabled={!hasValidSteps}
        >
          Analyze Funnel
        </Button>
      </Box>
    </>
  );
}
