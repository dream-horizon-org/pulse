import { Paper, Text, Button, Group } from "@mantine/core";
import { IconX } from "@tabler/icons-react";
import { SessionTimelineEvent } from "../../SessionTimeline.interface";
import { SpanDetails } from "./components/SpanDetails";
import { AttributeTabs } from "./components/AttributeTabs";
import classes from "./AttributesPanel.module.css";

interface AttributesPanelProps {
  selectedSpan: SessionTimelineEvent | null;
  onClose: () => void;
}

export function AttributesPanel({
  selectedSpan,
  onClose,
}: AttributesPanelProps) {
  if (!selectedSpan) return null;

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  const resourceAttributes = selectedSpan.attributes?.resource || {};
  const spanAttributes = selectedSpan.attributes?.span || {};
  const events = selectedSpan.attributes?.events || [];
  const links = selectedSpan.attributes?.links || [];

  return (
    <>
      <div className={classes.backdrop} onClick={handleBackdropClick} />
      <Paper className={classes.panel} shadow="xl" p="md">
        <div className={classes.header}>
          <Group gap="xs" align="center">
            <div className={classes.titleIndicator} />
            <Text className={classes.title}>Span Details</Text>
          </Group>
          <Button
            variant="subtle"
            size="xs"
            onClick={onClose}
            className={classes.closeButton}
          >
            <IconX size={14} />
          </Button>
        </div>

        <div className={classes.divider} />

        <div className={classes.content}>
          <div className={classes.scrollableContent}>
            <SpanDetails span={selectedSpan} />
            <div className={classes.divider} />
            <AttributeTabs
              resourceAttributes={resourceAttributes}
              spanAttributes={spanAttributes}
              events={events}
              links={links}
            />
          </div>
        </div>
      </Paper>
    </>
  );
}
