import { Box, Group, ActionIcon, Text, Tooltip } from "@mantine/core";
import { IconPlus, IconX, IconLink } from "@tabler/icons-react";
import { useState, useCallback } from "react";
import { AiSessionTab } from "../../hooks/useAiChatSessionManager";
import classes from "./AiChat.module.css";

export interface AiChatTabsProps {
  tabs: AiSessionTab[];
  activeSessionId: string | null;
  onSwitch: (sessionId: string) => void;
  onClose: (sessionId: string) => void;
  onNew: () => void;
}

export function AiChatTabs({
  tabs,
  activeSessionId,
  onSwitch,
  onClose,
  onNew,
}: AiChatTabsProps) {
  const [copied, setCopied] = useState(false);

  const handleCopyLink = useCallback(() => {
    if (!activeSessionId) return;
    const url = `${window.location.origin}${window.location.pathname}?s=${activeSessionId}`;
    navigator.clipboard.writeText(url).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [activeSessionId]);

  return (
    <Group gap={0} className={classes.tabsBar} wrap="nowrap">
      <Box className={classes.tabsList}>
        {tabs.map((tab) => {
          const isActive = tab.id === activeSessionId;
          return (
            <Box
              key={tab.id}
              className={`${classes.tab} ${isActive ? classes.tabActive : ""}`}
              onClick={() => onSwitch(tab.id)}
            >
              <Text size="xs" className={classes.tabTitle} truncate>
                {tab.title}
              </Text>
              <ActionIcon
                size="xs"
                variant="subtle"
                color="gray"
                onClick={(e) => {
                  e.stopPropagation();
                  onClose(tab.id);
                }}
                className={classes.tabClose}
              >
                <IconX size={12} />
              </ActionIcon>
            </Box>
          );
        })}
      </Box>
      <Group gap={4}>
        <Tooltip label={copied ? "Copied!" : "Copy chat link to share"}>
          <ActionIcon
            size="sm"
            variant="subtle"
            color="teal"
            onClick={handleCopyLink}
            disabled={!activeSessionId}
          >
            <IconLink size={14} />
          </ActionIcon>
        </Tooltip>
        <Tooltip label="New chat">
          <ActionIcon
            size="sm"
            variant="subtle"
            color="teal"
            onClick={onNew}
            className={classes.tabNew}
          >
            <IconPlus size={16} />
          </ActionIcon>
        </Tooltip>
      </Group>
    </Group>
  );
}
