import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box,
  Badge,
  Tabs,
} from "@mantine/core";
import { notifications } from "@mantine/notifications";
import {
  IconShieldLock,
  IconEye,
} from "@tabler/icons-react";
import {
  useInternalRoles,
  useRevokeRole,
  type InternalRoleMember,
} from "../../../hooks/useInternalRoles";
import { removeCookie } from "../../../helpers/cookies";
import { COOKIES_KEY, ROUTES, SYSTEM_ROLES } from "../../../constants";
import { PageHeader } from "../../../components/PageHeader";
import { ConfirmationModal } from "../../../components/ConfirmationModal";
import { RoleTable } from "./components/RoleTable";
import classes from "./DeveloperSettings.module.css";

type RoleType = typeof SYSTEM_ROLES[keyof typeof SYSTEM_ROLES];

interface PendingRevoke {
  userId: string;
  role: RoleType;
  label: string;
  /** Shown in confirmation (email preferred). */
  displayLabel: string;
}

export function DeveloperSettings() {
  const navigate = useNavigate();

  const { data: roles, isLoading, isError, error } = useInternalRoles();
  const revokeMutation = useRevokeRole({
    onLostSuperadminAccess: () =>
      navigate(ROUTES.INTERNAL_TENANT_SELECTOR.path, { replace: true }),
  });

  const [activeTab, setActiveTab] = useState<"superadmins" | "viewers">(
    "superadmins",
  );
  const [pendingRevoke, setPendingRevoke] = useState<PendingRevoke | null>(
    null,
  );

  useEffect(() => {
    if (!isError || !error) return;
    const status = (error as Error & { status?: number }).status;
    if (status !== 403) return;
    removeCookie(COOKIES_KEY.SYSTEM_ROLE);
    navigate(ROUTES.INTERNAL_TENANT_SELECTOR.path, { replace: true });
  }, [isError, error, navigate]);

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
      {
        onSettled: () => setPendingRevoke(null),
        onError: (e) => {
          notifications.show({
            title: "Could not revoke access",
            message:
              e instanceof Error ? e.message : "Something went wrong. Try again.",
            color: "red",
          });
        },
      },
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
            <RoleTable
              members={roles?.superadminMembers ?? []}
              role={SYSTEM_ROLES.SUPERADMIN}
              label="Superadmin"
              badgeColor="teal"
              isLoading={isLoading}
              isError={isError}
              error={error}
              onRevoke={handleRevoke}
              isRevokePending={revokeMutation.isPending}
              revokingUserId={(revokeMutation.variables as { userId: string } | undefined)?.userId}
            />
          </Tabs.Panel>

          <Tabs.Panel value="viewers" pt="md">
            <RoleTable
              members={roles?.internalViewerMembers ?? []}
              role={SYSTEM_ROLES.INTERNAL_VIEWER}
              label="Internal Viewer"
              badgeColor="blue"
              isLoading={isLoading}
              isError={isError}
              error={error}
              onRevoke={handleRevoke}
              isRevokePending={revokeMutation.isPending}
              revokingUserId={(revokeMutation.variables as { userId: string } | undefined)?.userId}
            />
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
