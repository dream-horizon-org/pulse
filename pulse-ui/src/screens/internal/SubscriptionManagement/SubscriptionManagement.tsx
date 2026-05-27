import { useNavigate } from "react-router-dom";
import { Box, Tabs, Badge } from "@mantine/core";
import { IconStack, IconChartBar, IconBuildingSkyscraper } from "@tabler/icons-react";
import { useInternalTiers } from "../../../hooks/useInternalTiers";
import { ROUTES } from "../../../constants";
import { PageHeader } from "../../../components/PageHeader";
import { TiersTab } from "./components/TiersTab";
import { ProjectQuotasTab } from "./components/ProjectQuotasTab";
import { TenantAssignmentTab } from "./components/TenantAssignmentTab";
import classes from "./SubscriptionManagement.module.css";

export function SubscriptionManagement() {
  const navigate = useNavigate();
  const { data: tiers, isLoading: tiersLoading } = useInternalTiers();
  const activeTierCount = tiers?.filter((t) => t.isActive).length ?? 0;

  return (
    <Box className={classes.container}>
      <PageHeader
        title="Subscription Management"
        subtitle="Manage tiers, project quotas, and tenant assignments — superadmin only"
        onBack={() => navigate(ROUTES.INTERNAL_TENANT_SELECTOR.path)}
      />

      <Box className={classes.content}>
        <Tabs defaultValue="tenants">
          <Tabs.List className={classes.tabsList}>
            <Tabs.Tab value="tenants" leftSection={<IconBuildingSkyscraper size={14} />}>
              Tenant Assignment
            </Tabs.Tab>
            <Tabs.Tab
              value="tiers"
              leftSection={<IconStack size={14} />}
              rightSection={
                !tiersLoading ? (
                  <Badge variant="light" color="teal" size="xs" circle>
                    {activeTierCount}
                  </Badge>
                ) : undefined
              }
            >
              Tiers
            </Tabs.Tab>
            <Tabs.Tab value="quotas" leftSection={<IconChartBar size={14} />}>
              Project Quotas
            </Tabs.Tab>
          </Tabs.List>

          <Tabs.Panel value="tenants" pt="md">
            <TenantAssignmentTab />
          </Tabs.Panel>

          <Tabs.Panel value="tiers" pt="md">
            <TiersTab />
          </Tabs.Panel>

          <Tabs.Panel value="quotas" pt="md">
            <ProjectQuotasTab />
          </Tabs.Panel>
        </Tabs>
      </Box>
    </Box>
  );
}
