import {
  ActionIcon,
  Box,
  Button,
  Code,
  CopyButton,
  Group,
  Modal,
  Stack,
  Table,
  Text,
  TextInput,
  Tooltip,
} from "@mantine/core";
import { useDisclosure } from "@mantine/hooks";
import { IconCheck, IconCopy, IconKey, IconTrash } from "@tabler/icons-react";
import { useState } from "react";
import {
  useCreateUserApiKey,
  useListUserApiKeys,
  useRevokeUserApiKey,
  type CreateUserApiKeyResponse,
} from "../../hooks/useUserApiKeys";
import classes from "./PersonalTokens.module.css";

export function PersonalTokens() {
  const { data: keys = [], isLoading } = useListUserApiKeys();
  const createMutation = useCreateUserApiKey();
  const revokeMutation = useRevokeUserApiKey();

  const [createOpened, { open: openCreate, close: closeCreate }] = useDisclosure(false);
  const [newKeyName, setNewKeyName] = useState("");
  const [createdKey, setCreatedKey] = useState<CreateUserApiKeyResponse | null>(null);

  const [revokeKeyId, setRevokeKeyId] = useState<number | null>(null);
  const [confirmOpened, { open: openConfirm, close: closeConfirm }] = useDisclosure(false);

  const handleCreate = async () => {
    if (!newKeyName.trim()) return;
    const result = await createMutation.mutateAsync(newKeyName.trim());
    setCreatedKey(result);
    setNewKeyName("");
  };

  const handleCloseCreate = () => {
    closeCreate();
    setCreatedKey(null);
    setNewKeyName("");
  };

  const handleRevokeClick = (id: number) => {
    setRevokeKeyId(id);
    openConfirm();
  };

  const handleRevokeConfirm = async () => {
    if (revokeKeyId == null) return;
    await revokeMutation.mutateAsync(revokeKeyId);
    closeConfirm();
    setRevokeKeyId(null);
  };

  return (
    <Box className={classes.container}>
      <Group justify="space-between" mb="lg">
        <Box>
          <Text size="xl" fw={600}>Personal Access Tokens</Text>
          <Text size="sm" c="dimmed" mt={4}>
            API keys for MCP and CLI access. Set{" "}
            <Code>PULSE_API_KEY</Code> in your MCP config.
          </Text>
        </Box>
        <Button leftSection={<IconKey size={16} />} onClick={openCreate}>
          Generate New Key
        </Button>
      </Group>

      {isLoading ? (
        <Text c="dimmed">Loading...</Text>
      ) : keys.length === 0 ? (
        <Text c="dimmed">No API keys yet. Generate one to get started.</Text>
      ) : (
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Name</Table.Th>
              <Table.Th>Key prefix</Table.Th>
              <Table.Th>Created</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {keys.map((key) => (
              <Table.Tr key={key.id}>
                <Table.Td>{key.displayName}</Table.Td>
                <Table.Td>
                  <Code>{key.keyPrefix}...</Code>
                </Table.Td>
                <Table.Td>
                  {new Date(key.createdAt).toLocaleDateString()}
                </Table.Td>
                <Table.Td>
                  <Tooltip label="Revoke key">
                    <ActionIcon
                      color="red"
                      variant="subtle"
                      onClick={() => handleRevokeClick(key.id)}
                    >
                      <IconTrash size={16} />
                    </ActionIcon>
                  </Tooltip>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      {/* Create modal */}
      <Modal
        opened={createOpened}
        onClose={handleCloseCreate}
        title={createdKey ? "Key created — copy it now" : "Generate new API key"}
        centered
      >
        {createdKey ? (
          <Stack>
            <Text size="sm" c="dimmed">
              This key will not be shown again. Copy it and store it safely.
            </Text>
            <Group gap="xs" wrap="nowrap">
              <Code block style={{ flex: 1, wordBreak: "break-all" }}>
                {createdKey.rawApiKey}
              </Code>
              <CopyButton value={createdKey.rawApiKey} timeout={2000}>
                {({ copied, copy }) => (
                  <Tooltip label={copied ? "Copied" : "Copy"} withArrow>
                    <ActionIcon
                      color={copied ? "teal" : "gray"}
                      variant="subtle"
                      onClick={copy}
                    >
                      {copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
                    </ActionIcon>
                  </Tooltip>
                )}
              </CopyButton>
            </Group>
            <Button onClick={handleCloseCreate}>Done</Button>
          </Stack>
        ) : (
          <Stack>
            <TextInput
              label="Key name"
              placeholder="e.g. Cursor MCP"
              value={newKeyName}
              onChange={(e) => setNewKeyName(e.currentTarget.value)}
              onKeyDown={(e) => e.key === "Enter" && handleCreate()}
            />
            <Group justify="flex-end">
              <Button variant="subtle" onClick={handleCloseCreate}>Cancel</Button>
              <Button
                onClick={handleCreate}
                loading={createMutation.isPending}
                disabled={!newKeyName.trim()}
              >
                Generate
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>

      {/* Revoke confirmation modal */}
      <Modal
        opened={confirmOpened}
        onClose={closeConfirm}
        title="Revoke API key"
        centered
        size="sm"
      >
        <Stack>
          <Text size="sm">
            This key will stop working on its next use. Are you sure?
          </Text>
          <Group justify="flex-end">
            <Button variant="subtle" onClick={closeConfirm}>Cancel</Button>
            <Button
              color="red"
              onClick={handleRevokeConfirm}
              loading={revokeMutation.isPending}
            >
              Revoke
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Box>
  );
}
