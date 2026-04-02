import {
  Table,
  Text,
  Badge,
  Progress,
  Box,
  Group,
  Tooltip,
  UnstyledButton,
} from "@mantine/core";
import {
  IconArrowDown,
  IconArrowRight,
  IconBug,
  IconAlertTriangle,
  IconExclamationCircle,
} from "@tabler/icons-react";
import {
  FunnelStepResult,
  FunnelStepHealth,
} from "../../../hooks/useGetFunnelData";

interface FunnelTableProps {
  steps: FunnelStepResult[];
  healthData?: FunnelStepHealth[];
  onStepClick?: (stepLevel: number, issueType: string) => void;
}

export function FunnelTable({ steps, healthData, onStepClick }: FunnelTableProps) {
  if (steps.length === 0) return null;

  const maxCount = steps[0].count;

  const getHealthForStep = (index: number): FunnelStepHealth | undefined => {
    if (!healthData) return undefined;
    return healthData.find((h) => h.stepLevel === index + 1);
  };

  return (
    <Table striped highlightOnHover withTableBorder withColumnBorders>
      <Table.Thead>
        <Table.Tr>
          <Table.Th style={{ width: 50 }}>#</Table.Th>
          <Table.Th>Step</Table.Th>
          <Table.Th style={{ width: 120 }}>Users</Table.Th>
          <Table.Th style={{ width: 200 }}>Progress</Table.Th>
          <Table.Th style={{ width: 130 }}>Conversion</Table.Th>
          <Table.Th style={{ width: 130 }}>Drop-off</Table.Th>
          {healthData && (
            <Table.Th style={{ width: 200 }}>Issues</Table.Th>
          )}
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {steps.map((step, index) => {
          const health = getHealthForStep(index);

          return (
            <Table.Tr key={step.stepName}>
              <Table.Td>
                <Badge size="sm" variant="filled" color="teal" circle>
                  {index + 1}
                </Badge>
              </Table.Td>
              <Table.Td>
                <Group gap={6}>
                  {index > 0 && <IconArrowDown size={14} color="#64748b" />}
                  <Text size="sm" fw={500}>
                    {step.stepName}
                  </Text>
                </Group>
              </Table.Td>
              <Table.Td>
                <Text size="sm" fw={600}>
                  {step.count.toLocaleString()}
                </Text>
              </Table.Td>
              <Table.Td>
                <Box>
                  <Progress
                    value={maxCount > 0 ? (step.count / maxCount) * 100 : 0}
                    color="teal"
                    size="lg"
                    radius="md"
                  />
                </Box>
              </Table.Td>
              <Table.Td>
                <Group gap={4}>
                  <IconArrowRight size={14} color="#0ba09a" />
                  <Text size="sm" fw={600} c="teal">
                    {step.conversionRate}%
                  </Text>
                </Group>
              </Table.Td>
              <Table.Td>
                {index === 0 ? (
                  <Text size="sm" c="dimmed">
                    —
                  </Text>
                ) : (
                  <Text
                    size="sm"
                    fw={600}
                    c={
                      step.dropoffRate > 50
                        ? "red"
                        : step.dropoffRate > 25
                          ? "orange"
                          : "gray"
                    }
                  >
                    {step.dropoffRate}%
                  </Text>
                )}
              </Table.Td>
              {healthData && (
                <Table.Td>
                  {health ? (
                    <Group gap={6} wrap="nowrap">
                      <IssueBadge
                        icon={<IconBug size={12} />}
                        count={health.crashUsers}
                        rate={health.crashRate}
                        color="red"
                        label="Crashes"
                        onClick={() => onStepClick?.(index + 1, "CRASH")}
                      />
                      <IssueBadge
                        icon={<IconAlertTriangle size={12} />}
                        count={health.anrUsers}
                        rate={health.anrRate}
                        color="orange"
                        label="ANRs"
                        onClick={() => onStepClick?.(index + 1, "ANR")}
                      />
                      <IssueBadge
                        icon={<IconExclamationCircle size={12} />}
                        count={health.nonFatalUsers}
                        rate={health.nonFatalRate}
                        color="yellow"
                        label="Non-Fatal"
                        onClick={() => onStepClick?.(index + 1, "NON_FATAL")}
                      />
                    </Group>
                  ) : (
                    <Text size="xs" c="dimmed">
                      —
                    </Text>
                  )}
                </Table.Td>
              )}
            </Table.Tr>
          );
        })}
      </Table.Tbody>
    </Table>
  );
}

function IssueBadge({
  icon,
  count,
  rate,
  color,
  label,
  onClick,
}: {
  icon: React.ReactNode;
  count: number;
  rate: number;
  color: string;
  label: string;
  onClick?: () => void;
}) {
  if (count === 0) {
    return (
      <Badge size="xs" variant="light" color="gray" leftSection={icon}>
        0
      </Badge>
    );
  }

  return (
    <Tooltip label={`${label}: ${count} users (${rate}%) — Click to view sessions`}>
      <UnstyledButton onClick={onClick}>
        <Badge
          size="xs"
          variant="filled"
          color={color}
          leftSection={icon}
          style={{ cursor: "pointer" }}
        >
          {count}
        </Badge>
      </UnstyledButton>
    </Tooltip>
  );
}
