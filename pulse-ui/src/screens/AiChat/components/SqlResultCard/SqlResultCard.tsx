import {
  Box,
  Code,
  CopyButton,
  ActionIcon,
  Tooltip,
  Text,
} from "@mantine/core";
import { IconCopy, IconCheck } from "@tabler/icons-react";
import { SqlResultCardProps } from "./SqlResultCard.interface";
import { AI_CHAT_TEXTS, AI_CHAT_LIMITS } from "../../AiChat.constants";
import classes from "./SqlResultCard.module.css";

export const SqlResultCard = ({ sql }: SqlResultCardProps) => {
  if (!sql) return null;

  return (
    <Box className={classes.container}>
      <div className={classes.header}>
        <Text size="xs" fw={600} c="dimmed">
          {AI_CHAT_TEXTS.GENERATED_SQL}
        </Text>
        <CopyButton
          value={sql}
          timeout={AI_CHAT_LIMITS.COPY_TOOLTIP_TIMEOUT_MS}
        >
          {({ copied, copy }) => (
            <Tooltip
              label={copied ? AI_CHAT_TEXTS.COPIED : AI_CHAT_TEXTS.COPY}
              withArrow
              position="left"
            >
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
