import { useState } from "react";
import {
  Box,
  Table,
  Badge,
  Text,
  Group,
  Select,
  Button,
} from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { useInternalTenants } from "../../../../hooks/useInternalTenants";
import { useInternalTiers } from "../../../../hooks/useInternalTiers";
import { useUpdateTenantTier } from "../../../../hooks/useUpdateTenantTier";
import { TableSkeleton } from "../../../../components/Skeletons";
import { ConfirmationModal } from "../../../../components/ConfirmationModal";

interface PendingAssignment {
  tenantId: string;
  tenantName: string;
  tierId: number;
  tierName: string;
}

export function TenantAssignmentTab() {
  const { data: tenants, isLoading: tenantsLoading } = useInternalTenants();
  const { data: tiers, isLoading: tiersLoading } = useInternalTiers();
  const updateMutation = useUpdateTenantTier();

  const [pendingAssignment, setPendingAssignment] =
    useState<PendingAssignment | null>(null);
  const [selectedTiers, setSelectedTiers] = useState<
    Record<string, string | null>
  >({});

  const activeTiers = (tiers ?? []).filter((t) => t.isActive);

  const getTierOptionsForTenant = (currentTierName: string | undefined) =>
    activeTiers
      .filter((t) => t.name !== (currentTierName ?? "free"))
      .map((t) => ({ value: String(t.tierId), label: t.displayName }));

  const handleAssign = (tenantId: string, tenantName: string) => {
    const tierId = selectedTiers[tenantId];
    if (!tierId) return;
    const tier = activeTiers.find((t) => String(t.tierId) === tierId);
    if (!tier) return;
    setPendingAssignment({
      tenantId,
      tenantName,
      tierId: tier.tierId,
      tierName: tier.displayName,
    });
  };

  const confirmAssign = () => {
    if (!pendingAssignment) return;
    updateMutation.mutate(
      {
        tenantId: pendingAssignment.tenantId,
        tierId: pendingAssignment.tierId,
      },
      {
        onSuccess: () => {
          notifications.show({
            message: `Tier updated for "${pendingAssignment.tenantName}"`,
            color: "teal",
          });
          setSelectedTiers((prev) => ({
            ...prev,
            [pendingAssignment.tenantId]: null,
          }));
          setPendingAssignment(null);
        },
        onError: (e) => {
          notifications.show({
            title: "Update failed",
            message: e instanceof Error ? e.message : "Unknown error",
            color: "red",
          });
          setPendingAssignment(null);
        },
      },
    );
  };

  if (tenantsLoading || tiersLoading)
    return <TableSkeleton columns={4} rows={6} />;

  return (
    <Box>
      <Table striped highlightOnHover withTableBorder>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Tenant Name</Table.Th>
            <Table.Th>Tenant ID</Table.Th>
            <Table.Th>Current Tier</Table.Th>
            <Table.Th style={{ width: 280 }}>Assign Tier</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {(tenants ?? []).map((tenant) => (
            <Table.Tr key={tenant.tenantId}>
              <Table.Td>
                <Text size="sm" fw={500}>
                  {tenant.tenantName}
                </Text>
              </Table.Td>
              <Table.Td>
                <Text size="xs" ff="monospace" c="dimmed">
                  {tenant.tenantId}
                </Text>
              </Table.Td>
              <Table.Td>
                <Badge
                  size="xs"
                  variant="light"
                  color={tenant.tier === "enterprise" ? "violet" : "gray"}
                >
                  {tenant.tier ?? "free"}
                </Badge>
              </Table.Td>
              <Table.Td>
                <Group gap="xs" wrap="nowrap">
                  <Select
                    size="xs"
                    placeholder="Select tier..."
                    data={getTierOptionsForTenant(tenant.tier ?? undefined)}
                    value={selectedTiers[tenant.tenantId] ?? null}
                    onChange={(v) =>
                      setSelectedTiers((prev) => ({
                        ...prev,
                        [tenant.tenantId]: v,
                      }))
                    }
                    style={{ flex: 1 }}
                  />
                  <Button
                    size="xs"
                    variant="light"
                    color="teal"
                    disabled={!selectedTiers[tenant.tenantId]}
                    loading={
                      updateMutation.isPending &&
                      pendingAssignment?.tenantId === tenant.tenantId
                    }
                    onClick={() =>
                      handleAssign(tenant.tenantId, tenant.tenantName)
                    }
                  >
                    Assign
                  </Button>
                </Group>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>

      <ConfirmationModal
        opened={!!pendingAssignment}
        onClose={() => setPendingAssignment(null)}
        onConfirm={confirmAssign}
        message={`Assign tier "${pendingAssignment?.tierName}" to tenant "${pendingAssignment?.tenantName}"?`}
        confirmLabel="Assign Tier"
        confirmColor="teal"
        severity="info"
        loading={updateMutation.isPending}
      />
    </Box>
  );
}
