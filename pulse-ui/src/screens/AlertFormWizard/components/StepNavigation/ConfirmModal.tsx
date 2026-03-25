/**
 * Confirmation Modal Component
 */

import React from "react";
import { Modal, Stack, Text, Button, Group } from "@mantine/core";
import { IconHelpCircle } from "@tabler/icons-react";

interface ConfirmModalProps {
  opened: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title?: string;
  message: string;
  isLoading?: boolean;
  isEditMode?: boolean;
}

export const ConfirmModal: React.FC<ConfirmModalProps> = ({
  opened,
  onClose,
  onConfirm,
  message,
  isLoading = false,
  isEditMode = false,
}) => (
  <Modal
    opened={opened}
    onClose={onClose}
    centered
    size="md"
    withCloseButton={false}
  >
    <Stack gap="md" align="center">
      <IconHelpCircle size={40} color="var(--mantine-color-teal-6)" />
      <Text size="sm" c="dimmed" ta="center">
        {message}
      </Text>
      <Group justify="center" gap="sm" w="100%">
        <Button variant="default" onClick={onClose} disabled={isLoading}>
          Cancel
        </Button>
        <Button color="teal" onClick={onConfirm} loading={isLoading}>
          {isEditMode ? "Update" : "Confirm"}
        </Button>
      </Group>
    </Stack>
  </Modal>
);
