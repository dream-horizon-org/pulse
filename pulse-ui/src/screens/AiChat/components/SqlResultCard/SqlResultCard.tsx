import { Box, Code, CopyButton, ActionIcon, Tooltip, Text } from "@mantine/core";
import { IconCopy, IconCheck } from "@tabler/icons-react";
import { SqlResultCardProps } from "./SqlResultCard.interface";
import classes from "./SqlResultCard.module.css";

export const SqlResultCard = ({ sql }: SqlResultCardProps) => {
  if (!sql) return null;

  return (
    <Box className={classes.container}>
      <div className={classes.header}>
        <Text size="xs" fw={600} c="dimmed">
          Generated SQL
        </Text>
        <CopyButton value={sql} timeout={2000}>
          {({ copied, copy }) => (
            <Tooltip label={copied ? "Copied" : "Copy"} withArrow position="left">
              <ActionIcon
                color={copied ? "teal" : "gray"}
                variant="subtle"
                onClick={copy}
                size="sm"
              >
                {copied ? <IconCheck size={14} /> : <IconCopy size={14} />}
              </ActionIcon>
            </Tooltip>
          )}
        </CopyButton>
      </div>
      <Code block className={classes.code}>
        {sql}
      </Code>
    </Box>
  );
};
