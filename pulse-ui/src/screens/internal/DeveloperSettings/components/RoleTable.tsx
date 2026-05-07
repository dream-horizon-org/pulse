import { useState } from "react";
import {
  Box,
  Table,
  Button,
  Text,
  Badge,
  TextInput,
  ActionIcon,
  Tooltip,
} from "@mantine/core";
import { IconTrash, IconPlus, IconSearch } from "@tabler/icons-react";
import { notifications } from "@mantine/notifications";
import type { InternalRoleMember } from "../../../../hooks/useInternalRoles";
import { useAssignRole } from "../../../../hooks/useInternalRoles";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { TableSkeleton } from "../../../../components/Skeletons";
import { SYSTEM_ROLES } from "../../../../constants";
import classes from "../DeveloperSettings.module.css";

type RoleType = typeof SYSTEM_ROLES[keyof typeof SYSTEM_ROLES];

interface RoleTableProps {
  members: InternalRoleMember[];
  role: RoleType;
  label: string;
  badgeColor: string;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  onRevoke: (member: InternalRoleMember, role: RoleType, label: string) => void;
  isRevokePending: boolean;
  revokingUserId?: string;
}

export function RoleTable({
  members,
  role,
  label,
  badgeColor,
  isLoading,
  isError,
  error,
  onRevoke,
  isRevokePending,
  revokingUserId,
}: RoleTableProps) {
  const [assignInput, setAssignInput] = useState("");
  const assignMutation = useAssignRole();

  const handleAssign = () => {
    if (!assignInput.trim()) return;
    assignMutation.mutate(
      { identifier: assignInput.trim(), role },
      {
        onSuccess: () => setAssignInput(""),
        onError: (e) => {
          notifications.show({
            title: "Could not assign role",
            message:
              e instanceof Error ? e.message : "Something went wrong. Try again.",
            color: "red",
          });
        },
      },
    );
  };

  if (isLoading) {
    return (
      <Box className={classes.tableWrapper}>
        <TableSkeleton columns={3} rows={5} />
      </Box>
    );
  }

  const isForbidden = isError && (error as Error & { status?: number }).status === 403;

  if (isError && !isForbidden) {
    return (
      <Box className={classes.stateWrapper}>
        <ErrorAndEmptyState
          message="Failed to load roles"
          description={(error as Error).message || "Something went wrong. Please refresh."}
        />
      </Box>
    );
  }

  return (
    <Box className={classes.roleSection}>
      {members.length === 0 ? (
        <Box className={classes.emptyState}>
          <Text size="sm" c="dimmed">
            No {label.toLowerCase()}s assigned yet.
          </Text>
        </Box>
      ) : (
        <Box className={classes.tableWrapper}>
          <Table
            striped
            highlightOnHover
            withTableBorder
            className={classes.table}
          >
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Email</Table.Th>
                <Table.Th>User ID</Table.Th>
                <Table.Th style={{ width: 160 }}>Role</Table.Th>
                <Table.Th style={{ width: 60 }} />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {members.map((m) => (
                <Table.Tr key={m.userId}>
                  <Table.Td>
                    <Text size="sm">{m.email ?? "—"}</Text>
                  </Table.Td>
                  <Table.Td>
                    <Text ff="monospace" size="xs" c="dimmed">
                      {m.userId}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Badge variant="light" color={badgeColor} size="sm">
                      {label}
                    </Badge>
                  </Table.Td>
                  <Table.Td>
                    <Tooltip label={`Remove ${label}`} withArrow>
                      <ActionIcon
                        color="red"
                        variant="subtle"
                        size="sm"
                        onClick={() => onRevoke(m, role, label)}
                        loading={isRevokePending && revokingUserId === m.userId}
                      >
                        <IconTrash size={14} />
                      </ActionIcon>
                    </Tooltip>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Box>
      )}

      {/* Add user row */}
      <Box className={classes.addUserBar}>
        <TextInput
          placeholder={`Email or user id (e.g. user-…) to assign ${label}`}
          leftSection={<IconSearch size={14} />}
          value={assignInput}
          onChange={(e) => setAssignInput(e.currentTarget.value)}
          onKeyDown={(e) => e.key === "Enter" && handleAssign()}
          size="sm"
          className={classes.addInput}
        />
        <Button
          leftSection={<IconPlus size={14} />}
          size="sm"
          color="teal"
          onClick={handleAssign}
          loading={assignMutation.isPending}
          disabled={!assignInput.trim()}
        >
          Assign {label}
        </Button>
      </Box>
    </Box>
  );
}
