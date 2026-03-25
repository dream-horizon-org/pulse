import { Modal, Stack, Text, Group, Button } from "@mantine/core";
import {
  IconAlertCircle,
  IconInfoCircle,
  IconTrash,
} from "@tabler/icons-react";
import { ConfirmationModalProps } from "./ConfirmationModal.interface";
import classes from "./ConfirmationModal.module.css";

export function ConfirmationModal({
  opened,
  onClose,
  onConfirm,
  message,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  confirmColor = "red",
  loading = false,
  severity = "warning",
}: ConfirmationModalProps) {
  const handleConfirm = async () => {
    await onConfirm();
  };

  const getSeverityIcon = () => {
    switch (severity) {
      case "danger":
        return <IconTrash size={40} className={classes.iconDanger} />;
      case "warning":
        return <IconAlertCircle size={40} className={classes.iconWarning} />;
      case "info":
        return <IconInfoCircle size={40} className={classes.iconInfo} />;
      default:
        return <IconAlertCircle size={40} className={classes.iconWarning} />;
    }
  };

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      centered
      size="md"
      closeOnClickOutside={!loading}
      closeOnEscape={!loading}
      withCloseButton={false}
    >
      <Stack gap="md" align="center">
        {getSeverityIcon()}
        <Text size="sm" c="dimmed" ta="center">
          {message}
        </Text>
        <Group justify="center" gap="sm" w="100%">
          <Button variant="default" onClick={onClose} disabled={loading}>
            {cancelLabel}
          </Button>
          <Button
            color={confirmColor}
            onClick={handleConfirm}
            loading={loading}
          >
            {confirmLabel}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
