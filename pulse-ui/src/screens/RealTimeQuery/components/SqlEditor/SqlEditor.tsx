import {
  Box,
  Paper,
  Group,
  Text,
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
  IconTrash,
} from "@tabler/icons-react";
import { useRef, useState } from "react";
import classes from "./SqlEditor.module.css";

interface SqlEditorProps {
  value: string;
  onChange: (value: string) => void;
  tableName?: string;
  isLoading?: boolean;
}

export function SqlEditor({
  value,
  onChange,
  tableName,
  isLoading = false,
}: SqlEditorProps) {
  const editorRef = useRef<unknown>(null);
  const [isFocused, setIsFocused] = useState(false);

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
        <Group justify="flex-end">
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
