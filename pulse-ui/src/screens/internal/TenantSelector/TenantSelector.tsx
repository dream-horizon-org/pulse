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
} from "@tabler/icons-react";
import { useInternalTenants } from "../../../hooks/useInternalTenants";
import { InternalTenant } from "../../../hooks/useInternalTenants/useInternalTenants.interface";
import { getCookies, setCookies } from "../../../helpers/cookies";
import { API_ROUTES, COOKIES_KEY, ROUTES, SYSTEM_ROLES } from "../../../constants";
import { useTenantContext, useProjectContext } from "../../../contexts";
import { TIERS } from "../../../constants/Tiers";
import { TENANT_ROLES } from "../../../constants/Roles";
import { PageHeader } from "../../../components/PageHeader";
import { ErrorAndEmptyState } from "../../../components/ErrorAndEmptyState";
import { TableSkeleton } from "../../../components/Skeletons";
import classes from "./TenantSelector.module.css";

export function TenantSelector() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: tenants, isLoading, isError } = useInternalTenants();
  const { setTenantInfo } = useTenantContext();
  const { clearProject } = useProjectContext();
  const systemRole = getCookies(COOKIES_KEY.SYSTEM_ROLE);
  const [search, setSearch] = useState("");

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

  const handleSelectTenant = (tenant: InternalTenant) => {
    const resolvedTenantRole = tenant.userRole || TENANT_ROLES.MEMBER;

    clearProject();
    sessionStorage.removeItem("pulse_last_project_id");
    queryClient.removeQueries({ queryKey: [API_ROUTES.GET_USER_PROJECTS.key] });

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

    navigate(`/${tenant.tenantId}/projects`);
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
          systemRole === SYSTEM_ROLES.SUPERADMIN ? (
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
    </Box>
  );
}
