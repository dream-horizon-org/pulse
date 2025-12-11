/**
 * Confirmation Modal Component
 */

import React from "react";
import { Modal, Box, Text, Button, Group } from "@mantine/core";
import classes from "./StepNavigation.module.css";

interface ConfirmModalProps {
  opened: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  isLoading?: boolean;
  isEditMode?: boolean;
}

export const ConfirmModal: React.FC<ConfirmModalProps> = ({
  opened,
  onClose,
  onConfirm,
  title,
  message,
  isLoading = false,
  isEditMode = false,
}) => (
  <Modal opened={opened} onClose={onClose} title={title} centered size="md">
    <Box className={classes.modalContent}>
      <Text size="sm" c="dimmed" mb="lg">
        {message}
      </Text>
      <Group justify="flex-end" gap="md">
        <Button variant="default" onClick={onClose}>
          Cancel
        </Button>
        <Button color="teal" onClick={onConfirm} loading={isLoading}>
          {isEditMode ? "Update" : "Confirm"}
        </Button>
      </Group>
    </Box>
  </Modal>
);

