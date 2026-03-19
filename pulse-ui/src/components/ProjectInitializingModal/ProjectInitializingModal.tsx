import { Modal, Stack, Loader, Text } from "@mantine/core";
import { ProjectInitializingModalProps } from "./ProjectInitializingModal.interface";
import classes from "./ProjectInitializingModal.module.css";

export function ProjectInitializingModal({
  opened,
}: ProjectInitializingModalProps) {
  return (
    <Modal
      opened={opened}
      onClose={() => {}}
      withCloseButton={false}
      closeOnClickOutside={false}
      closeOnEscape={false}
      centered
      classNames={{
        overlay: classes.overlay,
        content: classes.content,
      }}
      styles={{
        overlay: { zIndex: 10000 },
        content: { zIndex: 10001 },
      }}
    >
      <Stack align="center" gap="md" py="md">
        <Loader type="bars" size="lg" />
        <Text size="md" c="dimmed">
          Initializing project...
        </Text>
      </Stack>
    </Modal>
  );
}
