import { Modal, Stack, Text, Group, Button } from '@mantine/core';
import { IconAlertTriangle, IconInfoCircle } from '@tabler/icons-react';
import { ConfirmationModalProps } from './ConfirmationModal.interface';
import classes from './ConfirmationModal.module.css';

export function ConfirmationModal({
  opened,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  confirmColor = 'red',
  loading = false,
  severity = 'warning',
}: ConfirmationModalProps) {
  const handleConfirm = async () => {
    await onConfirm();
  };

  const getSeverityIcon = () => {
    switch (severity) {
      case 'danger':
        return <IconAlertTriangle size={48} className={classes.iconDanger} />;
      case 'warning':
        return <IconAlertTriangle size={48} className={classes.iconWarning} />;
      case 'info':
        return <IconInfoCircle size={48} className={classes.iconInfo} />;
      default:
        return <IconAlertTriangle size={48} className={classes.iconWarning} />;
    }
  };

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={title}
      centered
      size="md"
      closeOnClickOutside={!loading}
      closeOnEscape={!loading}
      withCloseButton={!loading}
    >
      <Stack gap="lg">
        <div className={classes.iconContainer}>
          {getSeverityIcon()}
        </div>

        <Text size="sm" c="dimmed" ta="center">
          {message}
        </Text>

        <Group justify="center" gap="sm" mt="md">
          <Button
            variant="default"
            onClick={onClose}
            disabled={loading}
          >
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
