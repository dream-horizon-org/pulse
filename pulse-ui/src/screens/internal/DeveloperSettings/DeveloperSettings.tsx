import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box,
  Table,
  Button,
  Text,
  Badge,
  TextInput,
  ActionIcon,
  Tabs,
  Tooltip,
} from "@mantine/core";
import {
  IconTrash,
  IconPlus,
  IconSearch,
  IconShieldLock,
  IconEye,
} from "@tabler/icons-react";
import {
  useInternalRoles,
  useAssignRole,
  useRevokeRole,
  type InternalRoleMember,
} from "../../../hooks/useInternalRoles";
import { getCookies, removeCookie } from "../../../helpers/cookies";
import { COOKIES_KEY, ROUTES } from "../../../constants";
import { PageHeader } from "../../../components/PageHeader";
import { ErrorAndEmptyState } from "../../../components/ErrorAndEmptyState";
import { TableSkeleton } from "../../../components/Skeletons";
import { ConfirmationModal } from "../../../components/ConfirmationModal";
import classes from "./DeveloperSettings.module.css";

type RoleType = "superadmin" | "internal_viewer";

interface PendingRevoke {
  userId: string;
  role: RoleType;
  label: string;
  /** Shown in confirmation (email preferred). */
  displayLabel: string;
}

export function DeveloperSettings() {
  const navigate = useNavigate();
  const systemRole = getCookies(COOKIES_KEY.SYSTEM_ROLE);

  const { data: roles, isLoading, isError, error } = useInternalRoles();
  const assignMutation = useAssignRole();
  const revokeMutation = useRevokeRole({
    onLostSuperadminAccess: () =>
      navigate(ROUTES.INTERNAL_TENANT_SELECTOR.path, { replace: true }),
  });

  const [assignInput, setAssignInput] = useState("");
  const [activeTab, setActiveTab] = useState<"superadmins" | "viewers">(
    "superadmins",
  );
  const [pendingRevoke, setPendingRevoke] = useState<PendingRevoke | null>(
    null,
  );

  useEffect(() => {
    if (systemRole !== "superadmin")
      navigate(ROUTES.INTERNAL_TENANT_SELECTOR.path, { replace: true });
  }, [systemRole, navigate]);

  useEffect(() => {
    if (!isError || !error) return;
    const status = (error as Error & { status?: number }).status;
    if (status !== 403) return;
    removeCookie(COOKIES_KEY.SYSTEM_ROLE);
    navigate(ROUTES.INTERNAL_TENANT_SELECTOR.path, { replace: true });
  }, [isError, error, navigate]);

  const handleAssign = (role: RoleType) => {
    if (!assignInput.trim()) return;
    assignMutation.mutate(
      { identifier: assignInput.trim(), role },
      { onSuccess: () => setAssignInput("") },
    );
  };

  const handleRevoke = (
    member: InternalRoleMember,
    role: RoleType,
    label: string,
  ) => {
    setPendingRevoke({
      userId: member.userId,
      role,
      label,
      displayLabel: member.email?.trim() || member.userId,
    });
  };

  const confirmRevoke = () => {
    if (!pendingRevoke) return;
    revokeMutation.mutate(
      { userId: pendingRevoke.userId, role: pendingRevoke.role },
      { onSettled: () => setPendingRevoke(null) },
    );
  };

  const renderRoleTable = (
    members: InternalRoleMember[],
    role: RoleType,
    label: string,
    badgeColor: string,
  ) => {
    if (isLoading) {
      return (
        <Box className={classes.tableWrapper}>
          <TableSkeleton columns={3} rows={5} />
        </Box>
      );
    }

    const isForbidden =
      isError && (error as Error & { status?: number }).status === 403;

    if (isError && !isForbidden) {
      return (
        <Box className={classes.stateWrapper}>
          <ErrorAndEmptyState
            message="Failed to load roles"
            description={
              (error as Error).message || "Something went wrong. Please refresh."
            }
          />
        </Box>
      );
    }

    return (
      <Box className={classes.roleSection}>
        {/* Table */}
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
                          onClick={() => handleRevoke(m, role, label)}
                          loading={
                            revokeMutation.isPending &&
                            (
                              revokeMutation.variables as
                                | { userId: string }
                                | undefined
                            )?.userId === m.userId
                          }
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
            onKeyDown={(e) => e.key === "Enter" && handleAssign(role)}
            size="sm"
            className={classes.addInput}
          />
          <Button
            leftSection={<IconPlus size={14} />}
            size="sm"
            color="teal"
            onClick={() => handleAssign(role)}
            loading={assignMutation.isPending}
            disabled={!assignInput.trim()}
          >
            Assign {label}
          </Button>
        </Box>
      </Box>
    );
  };

  const superadminCount = roles?.superadminMembers?.length ?? 0;
  const viewerCount = roles?.internalViewerMembers?.length ?? 0;

  return (
    <Box className={classes.container}>
      <PageHeader
        title="Developer Settings"
        subtitle="Manage internal system roles — superadmin access only"
        onBack={() => navigate(ROUTES.INTERNAL_TENANT_SELECTOR.path)}
      />

      <Box className={classes.content}>
        <Tabs
          value={activeTab}
          onChange={(v) => setActiveTab(v as typeof activeTab)}
        >
          <Tabs.List className={classes.tabsList}>
            <Tabs.Tab
              value="superadmins"
              leftSection={<IconShieldLock size={14} />}
              rightSection={
                !isLoading ? (
                  <Badge variant="light" color="teal" size="xs" circle>
                    {superadminCount}
                  </Badge>
                ) : undefined
              }
            >
              Superadmins
            </Tabs.Tab>
            <Tabs.Tab
              value="viewers"
              leftSection={<IconEye size={14} />}
              rightSection={
                !isLoading ? (
                  <Badge variant="light" color="blue" size="xs" circle>
                    {viewerCount}
                  </Badge>
                ) : undefined
              }
            >
              Internal Viewers
            </Tabs.Tab>
          </Tabs.List>

          <Tabs.Panel value="superadmins" pt="md">
            {renderRoleTable(
              roles?.superadminMembers ?? [],
              "superadmin",
              "Superadmin",
              "teal",
            )}
          </Tabs.Panel>

          <Tabs.Panel value="viewers" pt="md">
            {renderRoleTable(
              roles?.internalViewerMembers ?? [],
              "internal_viewer",
              "Internal Viewer",
              "blue",
            )}
          </Tabs.Panel>
        </Tabs>
      </Box>

      <ConfirmationModal
        opened={!!pendingRevoke}
        onClose={() => setPendingRevoke(null)}
        onConfirm={confirmRevoke}
        message={`Remove ${pendingRevoke?.label} access from "${pendingRevoke?.displayLabel}"? This cannot be undone.`}
        confirmLabel="Revoke Access"
        confirmColor="red"
        severity="danger"
        loading={revokeMutation.isPending}
      />
    </Box>
  );
}
