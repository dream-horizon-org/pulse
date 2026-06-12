/**
 * Delete Modal Component
 */

import React from "react";
import { Modal, Box, Text, Button } from "@mantine/core";
import classes from "../../AlertFormWizard.module.css";

interface DeleteModalProps {
  opened: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

export const DeleteModal: React.FC<DeleteModalProps> = ({ opened, onClose, onConfirm }) => (
  <Modal opened={opened} onClose={onClose} title="Delete Alert" centered size="md">
    <Box className={classes.modalContent}>
      <Text size="sm" c="dimmed" mb="lg">
        Are you sure you want to delete this alert? This action cannot be undone.
      </Text>
      <Box className={classes.modalActions}>
        <Button variant="default" onClick={onClose}>
          Cancel
        </Button>
        <Button color="red" onClick={onConfirm}>
          Delete
        </Button>
      </Box>
    </Box>
  </Modal>
);

