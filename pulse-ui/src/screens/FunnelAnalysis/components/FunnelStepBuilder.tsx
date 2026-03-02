import {
  Box,
  TextInput,
  ActionIcon,
  Select,
  Group,
  Text,
  Button,
  Tooltip,
} from "@mantine/core";
import { IconPlus, IconTrash, IconGripVertical } from "@tabler/icons-react";
import { FunnelStep } from "../../../hooks/useGetFunnelData";
import classes from "../FunnelAnalysis.module.css";

interface FunnelStepBuilderProps {
  steps: FunnelStep[];
  onStepsChange: (steps: FunnelStep[]) => void;
}

const DATA_TYPE_OPTIONS = [
  { value: "TRACES", label: "Traces (Spans)" },
  { value: "LOGS", label: "Logs (Events)" },
];

export function FunnelStepBuilder({
  steps,
  onStepsChange,
}: FunnelStepBuilderProps) {
  const addStep = () => {
    onStepsChange([
      ...steps,
      { eventName: "", dataType: "TRACES" },
    ]);
  };

  const removeStep = (index: number) => {
    if (steps.length <= 2) return;
    onStepsChange(steps.filter((_, i) => i !== index));
  };

  const updateStep = (index: number, field: keyof FunnelStep, value: string) => {
    const updated = [...steps];
    updated[index] = { ...updated[index], [field]: value };
    onStepsChange(updated);
  };

  return (
    <Box className={classes.stepsSection}>
      <Group justify="space-between" mb="md">
        <Text size="sm" fw={600} c="dark.6">
          Funnel Steps
        </Text>
        <Text size="xs" c="dimmed">
          Define at least 2 steps for your funnel
        </Text>
      </Group>

      {steps.map((step, index) => (
        <Box key={index}>
          {index > 0 && (
            <Box className={classes.stepConnector}>
              <Box className={classes.connectorLine} />
            </Box>
          )}
          <Box className={classes.stepRow}>
            <Box className={classes.stepNumber}>{index + 1}</Box>

            <IconGripVertical size={16} color="#94a3b8" style={{ flexShrink: 0 }} />

            <TextInput
              placeholder={`Step ${index + 1} event name (e.g. AppLaunch)`}
              value={step.eventName}
              onChange={(e) => updateStep(index, "eventName", e.currentTarget.value)}
              style={{ flex: 1 }}
              size="sm"
            />

            <Select
              data={DATA_TYPE_OPTIONS}
              value={step.dataType}
              onChange={(value) => updateStep(index, "dataType", value || "TRACES")}
              style={{ width: 160 }}
              size="sm"
              allowDeselect={false}
            />

            <TextInput
              placeholder="PulseType (optional)"
              value={step.pulseType || ""}
              onChange={(e) => updateStep(index, "pulseType", e.currentTarget.value)}
              style={{ width: 160 }}
              size="sm"
            />

            <Tooltip label={steps.length <= 2 ? "Minimum 2 steps required" : "Remove step"}>
              <ActionIcon
                variant="light"
                color="red"
                size="md"
                onClick={() => removeStep(index)}
                disabled={steps.length <= 2}
              >
                <IconTrash size={16} />
              </ActionIcon>
            </Tooltip>
          </Box>
        </Box>
      ))}

      <Button
        variant="light"
        color="teal"
        size="sm"
        leftSection={<IconPlus size={16} />}
        onClick={addStep}
        mt="md"
      >
        Add Step
      </Button>
    </Box>
  );
}
