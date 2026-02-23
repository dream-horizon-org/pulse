import {
  Box,
  Paper,
  Group,
  Text,
  Textarea,
  CopyButton,
  ActionIcon,
  Tooltip,
  Badge,
} from "@mantine/core";
import {
  IconSparkles,
  IconCheck,
  IconCopy,
  IconCode,
} from "@tabler/icons-react";
import { useCallback, KeyboardEvent } from "react";

import { REALTIME_QUERY_TEXTS } from "../../RealTimeQuery.constants";
import { AiQueryProps } from "./AiQuery.interface";
import classes from "./AiQuery.module.css";

const SUGGESTION_QUERIES = [
  "Show me the top 10 events in the last hour",
  "Count events by event name in the last 24 hours",
  "Show me error events from the last 30 minutes",
  "What are the most common event names today?",
];

export function AiQuery({
  query,
  onQueryChange,
  onExecute,
  executionState,
  isLoading,
  generatedSql,
}: AiQueryProps) {

  const handleSubmit = useCallback(() => {
    if (!query.trim() || isLoading) return;
    onExecute(query.trim());
  }, [query, isLoading, onExecute]);

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLTextAreaElement>) => {
      // Submit on Ctrl/Cmd + Enter
      if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
        e.preventDefault();
        handleSubmit();
      }
    },
    [handleSubmit]
  );

  const handleSuggestionClick = useCallback(
    (suggestion: string) => {
      onQueryChange(suggestion);
      // Auto-execute the suggestion
      onExecute(suggestion);
    },
    [onQueryChange, onExecute]
  );

  const showSqlPreview = generatedSql && executionState.status !== "idle";

  return (
    <Paper className={classes.container} withBorder>
      {/* Header */}
      <Box className={classes.header}>
        <Group justify="space-between">
          <Group gap="xs">
            <IconSparkles size={16} color="var(--mantine-color-teal-6)" />
            <Text size="sm" fw={600}>
              AI Query
            </Text>
            <Badge size="xs" variant="light" color="teal">
              Beta
            </Badge>
          </Group>
          <Text size="xs" c="dimmed">
            Press Ctrl+Enter to run
          </Text>
        </Group>
      </Box>

      {/* Body */}
      <Box className={classes.body}>
        {/* Text Input */}
        <Box className={classes.inputWrapper}>
          <Textarea
            className={classes.textarea}
            placeholder={REALTIME_QUERY_TEXTS.AI_QUERY_PLACEHOLDER}
            value={query}
            onChange={(e) => onQueryChange(e.currentTarget.value)}
            onKeyDown={handleKeyDown}
            minRows={4}
            maxRows={8}
            autosize
            disabled={isLoading}
          />
        </Box>

        {/* Suggestion Chips */}
        {!query.trim() && (
          <Box>
            <Text size="xs" c="dimmed" mb={6}>
              Try one of these:
            </Text>
            <Box className={classes.suggestionsRow}>
              {SUGGESTION_QUERIES.map((suggestion) => (
                <Box
                  key={suggestion}
                  className={classes.suggestionChip}
                  onClick={() => handleSuggestionClick(suggestion)}
                >
                  {suggestion}
                </Box>
              ))}
            </Box>
          </Box>
        )}

        {/* Generated SQL Preview */}
        {showSqlPreview && (
          <Box className={classes.sqlPreview}>
            <Box className={classes.sqlPreviewHeader}>
              <Group gap="xs">
                <IconCode size={14} color="var(--mantine-color-gray-6)" />
                <Text size="xs" fw={600} c="dimmed">
                  {REALTIME_QUERY_TEXTS.AI_GENERATED_SQL}
                </Text>
              </Group>
              <CopyButton value={generatedSql}>
                {({ copied, copy }) => (
                  <Tooltip label={copied ? "Copied!" : "Copy SQL"}>
                    <ActionIcon
                      variant="subtle"
                      size="xs"
                      color={copied ? "teal" : "gray"}
                      onClick={copy}
                    >
                      {copied ? (
                        <IconCheck size={12} />
                      ) : (
                        <IconCopy size={12} />
                      )}
                    </ActionIcon>
                  </Tooltip>
                )}
              </CopyButton>
            </Box>
            <Box className={classes.sqlPreviewBody}>
              <pre className={classes.sqlCode}>{generatedSql}</pre>
            </Box>
          </Box>
        )}
      </Box>

      {/* Footer */}
      <Box className={classes.footer}>
        <Group justify="space-between">
          <Text className={classes.hint}>
            Describe what data you want in plain English. The AI will generate
            and run the SQL for you.
          </Text>
          {query.trim() && (
            <Text size="xs" c="dimmed">
              {query.length} chars
            </Text>
          )}
        </Group>
      </Box>
    </Paper>
  );
}

