import { Box, Stack, Title, Text } from "@mantine/core";
import { IconSparkles } from "@tabler/icons-react";
import { AI_CHAT_TEXTS, SUGGESTED_QUERIES } from "../../AiChat.constants";
import { EmptyStateProps } from "./EmptyState.interface";
import classes from "./EmptyState.module.css";

export const EmptyState = ({ onSelectSuggestion }: EmptyStateProps) => (
  <Stack className={classes.container}>
    <IconSparkles size={40} stroke={1.5} className={classes.sparklesIcon} />
    <Title order={3} className={classes.title}>
      {AI_CHAT_TEXTS.WELCOME_TITLE}
    </Title>
    <Text size="sm" c="dimmed" className={classes.subtitle}>
      {AI_CHAT_TEXTS.WELCOME_SUBTITLE}
    </Text>
    <Box className={classes.chips}>
      {SUGGESTED_QUERIES.map((query) => (
        <Box
          key={query}
          className={classes.chip}
          onClick={() => onSelectSuggestion(query)}
        >
          {query}
        </Box>
      ))}
    </Box>
  </Stack>
);
