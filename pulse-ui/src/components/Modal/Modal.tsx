import React from "react";
import { Modal } from "@mantine/core";

interface DModalProps {
  visible: boolean;
  onClose: () => void;
  onSubmit?: () => void;
  title?: React.ReactNode;
  renderContent: () => React.ReactNode;
  size?: string | number;
  className?: string;
}

export const DModal: React.FC<DModalProps> = ({
  visible,
  onClose,
  title,
  renderContent,
  size,
  className,
}) => {
  return (
    <Modal
      className={className}
      opened={visible}
      onClose={onClose}
      size={size}
      title={title}
    >
      {renderContent()}
    </Modal>
  );
};
