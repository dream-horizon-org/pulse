import {
  Box,
  Stack,
  Text,
  ThemeIcon,
  Title,
  UnstyledButton,
} from "@mantine/core";
import { IconSparkles } from "@tabler/icons-react";
import { AI_CHAT_TEXTS, SUGGESTED_QUERIES } from "../../AiChat.constants";
import { EmptyStateProps } from "./EmptyState.interface";
import classes from "./EmptyState.module.css";

export const EmptyState = ({ onSelectSuggestion }: EmptyStateProps) => (
  <Stack
    className={classes.container}
    flex={1}
    align="center"
    justify="center"
    gap="lg"
    w="100%"
    miw={0}
  >
    <ThemeIcon size={56} radius="xl" variant="light" color="teal">
      <IconSparkles size={28} stroke={1.5} />
    </ThemeIcon>
    <Title order={3} ta="center" c="dark.7" className={classes.title}>
      {AI_CHAT_TEXTS.WELCOME_TITLE}
    </Title>
    <Text size="sm" c="dimmed" ta="center" className={classes.subtitle}>
      {AI_CHAT_TEXTS.WELCOME_SUBTITLE}
    </Text>
    <Box className={classes.chips}>
      {SUGGESTED_QUERIES.map((query) => (
        <UnstyledButton
          key={query}
          className={classes.chip}
          onClick={() => onSelectSuggestion(query)}
        >
          {query}
        </UnstyledButton>
      ))}
    </Box>
  </Stack>
);
