import {
  Box,
  Paper,
  Group,
  Text,
  Button,
  Badge,
  Stack,
  CopyButton,
  ActionIcon,
  Tooltip,
} from "@mantine/core";
import Editor from "@monaco-editor/react";
import {
  IconCheck,
  IconCopy,
  IconSparkles,
  IconAlertCircle,
} from "@tabler/icons-react";
import { useRef, useState } from "react";
import classes from "./SqlEditor.module.css";

interface SqlEditorProps {
  value: string;
  onChange: (value: string) => void;
  onValidate?: () => void;
  isValidating?: boolean;
  isValid?: boolean;
}

const DEFAULT_SQL = `-- Write your SQL query here
-- Example: Get event counts by name for the last hour

SELECT 
    eventName,
    COUNT(*) as event_count,
    AVG(duration) as avg_duration
FROM 
    d11_stream_analytics_multi_region.processed_events_partitioned_hourly 
WHERE 
    eventTimestamp BETWEEN TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 HOUR) 
    AND CURRENT_TIMESTAMP()
GROUP BY 
    eventName
ORDER BY 
    event_count DESC
LIMIT 100;`;

const EXAMPLE_QUERIES = [
  {
    name: "Event Counts",
    query: `SELECT eventName, COUNT(*) as count
FROM processed_events_partitioned_hourly
WHERE eventTimestamp > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 24 HOUR)
GROUP BY eventName
ORDER BY count DESC
LIMIT 20;`,
  },
  {
    name: "User Sessions",
    query: `SELECT 
    platform,
    COUNT(DISTINCT sessionId) as sessions,
    COUNT(DISTINCT userId) as users
FROM processed_events_partitioned_hourly
WHERE eventTimestamp > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY)
GROUP BY platform;`,
  },
  {
    name: "Error Analysis",
    query: `SELECT 
    eventName,
    app_version,
    COUNT(*) as error_count
FROM processed_events_partitioned_hourly
WHERE eventName LIKE '%Error%'
    AND eventTimestamp > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 24 HOUR)
GROUP BY eventName, app_version
ORDER BY error_count DESC
LIMIT 50;`,
  },
];

export function SqlEditor({
  value,
  onChange,
  onValidate,
  isValidating = false,
  isValid,
}: SqlEditorProps) {
  const editorRef = useRef<any>(null);
  const [showExamples, setShowExamples] = useState(false);

  const handleEditorMount = (editor: any) => {
    editorRef.current = editor;
  };

  const handleEditorChange = (newValue: string | undefined) => {
    onChange(newValue || "");
  };

  const insertExample = (query: string) => {
    onChange(query);
    setShowExamples(false);
  };

  const displayValue = value || DEFAULT_SQL;

  return (
    <Paper className={classes.container} withBorder>
      {/* Editor Header */}
      <Box className={classes.header}>
        <Group justify="space-between">
          <Group gap="xs">
            <Text size="sm" fw={600}>SQL Editor</Text>
            <Badge
              size="xs"
              variant="light"
              color={isValid === undefined ? "gray" : isValid ? "green" : "red"}
            >
              {isValid === undefined ? "Not validated" : isValid ? "Valid" : "Invalid"}
            </Badge>
          </Group>
          <Group gap="xs">
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
            <CopyButton value={displayValue}>
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
            <Button
              variant="light"
              size="xs"
              leftSection={<IconCheck size={14} />}
              onClick={onValidate}
              loading={isValidating}
              color="teal"
            >
              Validate
            </Button>
          </Group>
        </Group>
      </Box>

      {/* Example Queries Dropdown */}
      {showExamples && (
        <Box className={classes.examplesDropdown}>
          <Stack gap="xs">
            <Text size="xs" fw={600} c="dimmed" tt="uppercase">
              Example Queries
            </Text>
            {EXAMPLE_QUERIES.map((example) => (
              <Paper
                key={example.name}
                className={classes.exampleCard}
                p="xs"
                withBorder
                onClick={() => insertExample(example.query)}
              >
                <Text size="xs" fw={500}>{example.name}</Text>
                <Text size="xs" c="dimmed" lineClamp={1}>
                  {example.query.split("\n")[0]}...
                </Text>
              </Paper>
            ))}
          </Stack>
        </Box>
      )}

      {/* Monaco Editor */}
      <Box className={classes.editorWrapper}>
        <Editor
          height="350px"
          defaultLanguage="sql"
          value={displayValue}
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
          }}
        />
      </Box>

      {/* Editor Footer */}
      <Box className={classes.footer}>
        <Group justify="space-between">
          <Group gap="xs">
            {isValid === false && (
              <Group gap={4}>
                <IconAlertCircle size={14} color="var(--mantine-color-red-6)" />
                <Text size="xs" c="red">
                  Query validation failed. Check your syntax.
                </Text>
              </Group>
            )}
            {isValid === true && (
              <Group gap={4}>
                <IconCheck size={14} color="var(--mantine-color-green-6)" />
                <Text size="xs" c="green">
                  Query is valid and ready to run.
                </Text>
              </Group>
            )}
          </Group>
          <Group gap="xs">
            <Text size="xs" c="dimmed">
              {displayValue.length} characters
            </Text>
            <Text size="xs" c="dimmed">•</Text>
            <Text size="xs" c="dimmed">
              {displayValue.split("\n").length} lines
            </Text>
          </Group>
        </Group>
      </Box>
    </Paper>
  );
}
