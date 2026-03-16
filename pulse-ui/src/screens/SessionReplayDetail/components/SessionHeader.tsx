import { Group, Button } from "@mantine/core";
import { IconArrowLeft } from "@tabler/icons-react";
import { LABELS } from "../constants/strings";

interface SessionHeaderProps {
  onBack: () => void;
}

export function SessionHeader({ onBack }: SessionHeaderProps) {
  return (
    <Group mb="sm">
      <Button
        variant="subtle"
        color="teal"
        leftSection={<IconArrowLeft size={16} />}
        onClick={onBack}
      >
        {LABELS.BACK}
      </Button>
    </Group>
  );
}
