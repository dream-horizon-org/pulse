/**
 * Wizard Header Component
 */

import React from "react";
import { Box, Title, Button, Tooltip } from "@mantine/core";
import { IconX, IconTrash } from "@tabler/icons-react";
import classes from "../../AlertFormWizard.module.css";

interface WizardHeaderProps {
  isUpdateFlow: boolean;
  onDelete: () => void;
  onClose: () => void;
}

export const WizardHeader: React.FC<WizardHeaderProps> = ({ isUpdateFlow, onDelete, onClose }) => (
  <Box className={classes.wizardHeader}>
    <Title order={2} className={classes.wizardTitle}>
      {isUpdateFlow ? "Update Alert" : "Create Alert"}
    </Title>
    <Box className={classes.headerActions}>
      {isUpdateFlow && (
        <Button onClick={onDelete} size="sm" variant="outline" color="red" aria-label="Delete alert">
          <Tooltip label="Delete Alert">
            <IconTrash size={18} />
          </Tooltip>
        </Button>
      )}
      <Button onClick={onClose} size="sm" variant="subtle" aria-label="Close form" className={classes.closeButton}>
        <IconX size={20} />
      </Button>
    </Box>
  </Box>
);

