import { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import {
  Box,
  Table,
  Button,
  Text,
  TextInput,
} from "@mantine/core";
import {
  IconSearch,
  IconArrowRight,
  IconBuildingSkyscraper,
  IconSettings,
  IconPlus,
  IconLogout,
} from "@tabler/icons-react";
import { useInternalTenants } from "../../../hooks/useInternalTenants";
import { InternalTenant } from "../../../hooks/useInternalTenants/useInternalTenants.interface";
import type { TenantResponse } from "./TenantSelector.interface";
import { getCookies, setCookies } from "../../../helpers/cookies";
import { getAndSetAccessTokenFromRefreshToken } from "../../../helpers/getAccessTokenFromRefreshToken";
import { COOKIES_KEY, ROUTES, SYSTEM_ROLES } from "../../../constants";
import { useTenantContext, useProjectContext } from "../../../contexts";
import { TIERS } from "../../../constants/Tiers";
import { TENANT_ROLES } from "../../../constants/Roles";
import { PageHeader } from "../../../components/PageHeader";
import { ErrorAndEmptyState } from "../../../components/ErrorAndEmptyState";
import { TableSkeleton } from "../../../components/Skeletons";
import { ConfirmationModal } from "../../../components/ConfirmationModal";
import { performLogout } from "../../../helpers/logout";
import { CreateTenantModal } from "./components";
import classes from "./TenantSelector.module.css";

export function TenantSelector() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: tenants, isLoading, isError } = useInternalTenants();
  const { setTenantInfo, clearTenant } = useTenantContext();
  const { clearProject } = useProjectContext();
  const systemRole = getCookies(COOKIES_KEY.SYSTEM_ROLE);
  const [search, setSearch] = useState("");
  const [isCreateTenantOpen, setIsCreateTenantOpen] = useState(false);
  const [logoutModalOpened, setLogoutModalOpened] = useState(false);

  useEffect(() => {
    if (!systemRole) navigate(ROUTES.LOGIN.basePath, { replace: true });
  }, [systemRole, navigate]);

  const filteredTenants = useMemo(() => {
    if (!tenants) return [];
    const q = search.trim().toLowerCase();
    if (!q) return tenants;
    return tenants.filter(
      (t) =>
        t.tenantName.toLowerCase().includes(q) ||
        t.tenantId.toLowerCase().includes(q),
    );
  }, [tenants, search]);

  const handleSelectTenant = async (tenant: InternalTenant) => {
    const resolvedTenantRole = tenant.userRole || TENANT_ROLES.MEMBER;

    clearProject();
    sessionStorage.removeItem("pulse_last_project_id");
    queryClient.removeQueries({ predicate: () => true });

    setCookies(COOKIES_KEY.TENANT_ID, tenant.tenantId);
    setCookies(COOKIES_KEY.TENANT_NAME, tenant.tenantName);
    setCookies(COOKIES_KEY.TENANT_ROLE, resolvedTenantRole);
    setCookies(COOKIES_KEY.TIER, tenant.tier || TIERS.FREE);

    setTenantInfo({
      tenantId: tenant.tenantId,
      tenantName: tenant.tenantName,
      userRole: resolvedTenantRole,
      tier:
        (tenant.tier as (typeof TIERS)[keyof typeof TIERS]) || TIERS.FREE,
    });

    // Await the token refresh so the new JWT (carrying the selected tenantId)
    // is in the cookie before navigation — prevents a race where the first
    // API call on the projects page triggers its own refresh without tenantId
    // and overwrites the cookie with tenantId:"default".
    await getAndSetAccessTokenFromRefreshToken(tenant.tenantId).catch(() => {
      // Non-fatal: stale token will be rotated on the next 401 cycle.
    });

    navigate(`/${tenant.tenantId}/projects`);
  };

  const handleEnterWorkspace = (tenant: TenantResponse) => {
    handleSelectTenant({
      tenantId: tenant.tenantId,
      tenantName: tenant.name,
      userRole: TENANT_ROLES.ADMIN,
      tier: TIERS.FREE,
    });
    setIsCreateTenantOpen(false);
  };

  const onLogoutClick = async () => {
    setLogoutModalOpened(false);

    clearTenant();
    await performLogout();

    navigate(ROUTES.LOGIN.basePath);
  };

  const renderContent = () => {
    if (isLoading) {
      return (
        <Box className={classes.tableWrapper}>
          <TableSkeleton columns={4} rows={8} />
        </Box>
      );
    }

    if (isError) {
      return (
        <Box className={classes.stateWrapper}>
          <ErrorAndEmptyState
            message="Failed to load tenants"
            description="Something went wrong fetching tenant data. Please refresh."
            icon={<IconBuildingSkyscraper size={36} color="var(--mantine-color-gray-5)" />}
          />
        </Box>
      );
    }

    if (!filteredTenants.length) {
      return (
        <Box className={classes.emptyState}>
          <IconBuildingSkyscraper size={40} className={classes.emptyIcon} />
          <Text className={classes.emptyTitle}>
            {search ? "No matching tenants" : "No tenants available"}
          </Text>
          <Text className={classes.emptyDescription}>
            {search
              ? `No tenants match "${search}". Try a different search term.`
              : "There are no tenants accessible with your current role."}
          </Text>
        </Box>
      );
    }

    return (
      <Box className={classes.tableWrapper}>
        <Table
          striped
          highlightOnHover
          withTableBorder
          className={classes.table}
        >
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Tenant Name</Table.Th>
              <Table.Th>Tenant ID</Table.Th>
              <Table.Th style={{ width: 80 }} />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {filteredTenants.map((tenant) => (
              <Table.Tr
                key={tenant.tenantId}
                className={classes.tableRow}
                onClick={() => handleSelectTenant(tenant)}
              >
                <Table.Td>
                  <Text fw={500} size="sm">
                    {tenant.tenantName}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="xs" c="dimmed" ff="monospace">
                    {tenant.tenantId}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Button
                    size="xs"
                    variant="light"
                    color="teal"
                    rightSection={<IconArrowRight size={12} />}
                    onClick={(e) => {
                      e.stopPropagation();
                      handleSelectTenant(tenant);
                    }}
                  >
                    Enter
                  </Button>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Box>
    );
  };

  return (
    <Box className={classes.container}>
      <PageHeader
        title="Internal Workspaces"
        subtitle={`Signed in as ${systemRole} · Select a tenant to impersonate`}
        count={tenants?.length}
        countLabel={tenants?.length === 1 ? "Tenant" : "Tenants"}
        actions={
          systemRole === SYSTEM_ROLES.SUPERADMIN || systemRole === SYSTEM_ROLES.INTERNAL_VIEWER ? (
            <Box style={{ display: "flex", gap: "8px" }}>
              <Button
                variant="light"
                color="teal"
                size="sm"
                leftSection={<IconPlus size={14} />}
                onClick={() => setIsCreateTenantOpen(true)}
              >
                Create Tenant
              </Button>
              {systemRole === SYSTEM_ROLES.SUPERADMIN ? (
                <Button
                  variant="light"
                  color="teal"
                  size="sm"
                  leftSection={<IconSettings size={14} />}
                  onClick={() =>
                    navigate(ROUTES.INTERNAL_DEVELOPER_SETTINGS.path)
                  }
                >
                  Developer Settings
                </Button>
              ) : null}
              <Button
                variant="light"
                color="red"
                size="sm"
                leftSection={<IconLogout size={14} />}
                onClick={() => setLogoutModalOpened(true)}
              >
                Logout
              </Button>
            </Box>
          ) : undefined
        }
      />

      <Box className={classes.content}>
        {/* Search controls */}
        <Box className={classes.controlsBar}>
          <TextInput
            className={classes.searchInput}
            placeholder="Search by name or tenant ID..."
            leftSection={<IconSearch size={16} />}
            value={search}
            onChange={(e) => setSearch(e.currentTarget.value)}
            size="sm"
          />
        </Box>

        {/* Table / states */}
        {renderContent()}
      </Box>

      <CreateTenantModal
        opened={isCreateTenantOpen}
        onClose={() => setIsCreateTenantOpen(false)}
        onEnterWorkspace={handleEnterWorkspace}
      />

      <ConfirmationModal
        opened={logoutModalOpened}
        onClose={() => setLogoutModalOpened(false)}
        onConfirm={onLogoutClick}
        title="Logout"
        message="Are you sure you want to logout?"
        confirmLabel="Logout"
        confirmColor="red"
      />
    </Box>
  );
}
