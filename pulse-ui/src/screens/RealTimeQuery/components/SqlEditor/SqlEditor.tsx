import {
  Box,
  Paper,
  Group,
  Text,
  Button,
  Stack,
  CopyButton,
  ActionIcon,
  Tooltip,
  Skeleton,
} from "@mantine/core";
import Editor from "@monaco-editor/react";
import {
  IconCheck,
  IconCopy,
  IconSparkles,
  IconTrash,
} from "@tabler/icons-react";
import { useRef, useState, useMemo } from "react";
import classes from "./SqlEditor.module.css";

interface SqlEditorProps {
  value: string;
  onChange: (value: string) => void;
  tableName?: string;
  isLoading?: boolean;
}

// Generate example queries based on actual table name
function generateExampleQueries(tableName: string) {
  return [
    {
      name: "Recent Events",
      description: "Get last 100 events",
      query: `SELECT *
FROM ${tableName}
ORDER BY eventTimestamp DESC
LIMIT 100;`,
    },
    {
      name: "Event Counts",
      description: "Count events by name",
      query: `SELECT 
    eventName,
    COUNT(*) as event_count
FROM ${tableName}
WHERE eventTimestamp >= date_add('hour', -24, current_timestamp)
GROUP BY eventName
ORDER BY event_count DESC
LIMIT 20;`,
    },
    {
      name: "User Sessions",
      description: "Sessions per platform",
      query: `SELECT 
    platform,
    COUNT(DISTINCT sessionId) as sessions,
    COUNT(DISTINCT userId) as users
FROM ${tableName}
WHERE eventTimestamp >= date_add('day', -7, current_timestamp)
GROUP BY platform
ORDER BY sessions DESC;`,
    },
    {
      name: "Hourly Trend",
      description: "Events per hour (last 24h)",
      query: `SELECT 
    date_trunc('hour', eventTimestamp) as hour,
    COUNT(*) as event_count
FROM ${tableName}
WHERE eventTimestamp >= date_add('hour', -24, current_timestamp)
GROUP BY date_trunc('hour', eventTimestamp)
ORDER BY hour DESC;`,
    },
  ];
}

export function SqlEditor({
  value,
  onChange,
  tableName,
  isLoading = false,
}: SqlEditorProps) {
  const editorRef = useRef<unknown>(null);
  const [showExamples, setShowExamples] = useState(false);
  const [isFocused, setIsFocused] = useState(false);

  // Generate example queries when table name is available
  const exampleQueries = useMemo(() => {
    if (!tableName) return [];
    return generateExampleQueries(tableName);
  }, [tableName]);

  const handleEditorMount = (editor: { onDidFocusEditorWidget: (cb: () => void) => void; onDidBlurEditorWidget: (cb: () => void) => void }) => {
    editorRef.current = editor;
    
    // Listen for focus/blur events on the editor
    editor.onDidFocusEditorWidget(() => {
      setIsFocused(true);
    });
    
    editor.onDidBlurEditorWidget(() => {
      setIsFocused(false);
    });
  };

  const handleEditorChange = (newValue: string | undefined) => {
    onChange(newValue || "");
  };

  const insertExample = (query: string) => {
    onChange(query);
    setShowExamples(false);
  };

  const clearQuery = () => {
    onChange("");
  };

  // Check if editor has actual content
  const hasContent = value.trim().length > 0;
  
  // Show placeholder when empty and not focused
  const showPlaceholder = !hasContent && !isFocused;

  return (
    <Paper className={classes.container} withBorder>
      {/* Editor Header */}
      <Box className={classes.header}>
        <Group justify="space-between">
          <Group gap="xs">
            <Text size="sm" fw={600}>SQL Editor</Text>
            {tableName && (
              <Text size="xs" c="dimmed">
                Table: {tableName}
              </Text>
            )}
          </Group>
          <Group gap="xs">
            {exampleQueries.length > 0 && (
              <Tooltip label="Example Queries">
                <Button
                  variant="subtle"
                  size="xs"
                  leftSection={<IconSparkles size={14} />}
                  onClick={() => setShowExamples(!showExamples)}
                >
                  Examples
                </Button>
              </Tooltip>
            )}
            {hasContent && (
              <>
                <CopyButton value={value}>
                  {({ copied, copy }) => (
                    <Tooltip label={copied ? "Copied!" : "Copy query"}>
                      <ActionIcon
                        variant="subtle"
                        size="sm"
                        color={copied ? "teal" : "gray"}
                        onClick={copy}
                      >
                        {copied ? <IconCheck size={14} /> : <IconCopy size={14} />}
                      </ActionIcon>
                    </Tooltip>
                  )}
                </CopyButton>
                <Tooltip label="Clear query">
                  <ActionIcon
                    variant="subtle"
                    size="sm"
                    color="gray"
                    onClick={clearQuery}
                  >
                    <IconTrash size={14} />
                  </ActionIcon>
                </Tooltip>
              </>
            )}
          </Group>
        </Group>
      </Box>

      {/* Example Queries Dropdown */}
      {showExamples && exampleQueries.length > 0 && (
        <Box className={classes.examplesDropdown}>
          <Stack gap="xs">
            <Text size="xs" fw={600} c="dimmed" tt="uppercase">
              Example Queries
            </Text>
            {exampleQueries.map((example) => (
              <Paper
                key={example.name}
                className={classes.exampleCard}
                p="xs"
                withBorder
                onClick={() => insertExample(example.query)}
              >
                <Text size="xs" fw={500}>{example.name}</Text>
                <Text size="xs" c="dimmed">{example.description}</Text>
              </Paper>
            ))}
          </Stack>
        </Box>
      )}

      {/* Monaco Editor with Placeholder */}
      <Box className={classes.editorWrapper}>
        <Box className={classes.editorInner}>
          {isLoading ? (
            <Stack gap="xs" className={classes.loadingWrapper}>
              <Skeleton height={20} width="60%" />
              <Skeleton height={20} width="80%" />
              <Skeleton height={20} width="40%" />
              <Skeleton height={20} width="70%" />
              <Skeleton height={20} width="50%" />
            </Stack>
          ) : (
            <>
              {/* Placeholder overlay */}
              {showPlaceholder && (
                <Box className={classes.placeholder}>
                  <Text size="sm" c="dimmed" className={classes.placeholderText}>
                    Write your SQL query here...
                  </Text>
                  {tableName && (
                    <Text size="xs" c="dimmed" mt="xs" className={classes.placeholderHint}>
                      Try clicking "Examples" above or start typing
                    </Text>
                  )}
                </Box>
              )}
              <Editor
                height="100%"
                defaultLanguage="sql"
                value={value}
                onChange={handleEditorChange}
                onMount={handleEditorMount}
                theme="vs-light"
                options={{
                  minimap: { enabled: false },
                  fontSize: 13,
                  fontFamily: "'SF Mono', 'Monaco', 'Inconsolata', 'Fira Code', monospace",
                  wordWrap: "on",
                  automaticLayout: true,
                  lineNumbers: "on",
                  scrollBeyondLastLine: false,
                  tabSize: 4,
                  insertSpaces: true,
                  renderLineHighlight: "line",
                  selectOnLineNumbers: true,
                  folding: true,
                  matchBrackets: "always",
                  autoClosingBrackets: "always",
                  formatOnPaste: true,
                  suggest: {
                    showKeywords: true,
                  },
                  padding: {
                    top: 10,
                  },
                }}
              />
            </>
          )}
        </Box>
      </Box>

      {/* Editor Footer */}
      <Box className={classes.footer}>
        <Group justify="space-between">
          <Text size="xs" c="dimmed">
            {hasContent ? "Press Ctrl/Cmd + Enter to run query (coming soon)" : "Start typing to write your query"}
          </Text>
          <Group gap="xs">
            <Text size="xs" c="dimmed">
              {value.length} chars
            </Text>
            <Text size="xs" c="dimmed">•</Text>
            <Text size="xs" c="dimmed">
              {value ? value.split("\n").length : 0} lines
            </Text>
          </Group>
        </Group>
      </Box>
    </Paper>
  );
}
