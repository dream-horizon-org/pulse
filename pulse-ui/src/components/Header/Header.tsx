import { AppShell, Group, Text, Box, Select, Badge } from "@mantine/core";

import classes from "./Header.module.css";
import { HeaderProps } from "./Header.interface";
import { useNavigate } from "react-router-dom";
import { IconFolder, IconBuilding } from "@tabler/icons-react";
import { useTenantContext, useProjectContext } from "../../contexts";
import { TIERS } from "../../constants/Tiers";

export function Header({ toggle: toogle, opened }: HeaderProps) {
  const navigate = useNavigate();
  const { projects, tier, tenantName } = useTenantContext();
  const { projectId, projectName, navigateToProject } = useProjectContext();

  const handleProjectSwitch = async (newProjectId: string | null) => {
    if (!newProjectId || newProjectId === projectId) return;
    await navigateToProject(newProjectId);
  };

  return (
    <>
      <AppShell.Header>
        <Box className={classes.headerContainer}>
          {/* Organization Name Section */}
          <Box className={classes.leftSection}>
            <Group gap="xs">
              <IconBuilding size={20} style={{ color: "#0ba09a" }} />
              <Text fw={600} size="md" className={classes.orgName}>
                {tenantName || "Organization"}
              </Text>
            </Group>
          </Box>

          {/* Project Display Section - 64px gap from organization */}
          <Box
            className={classes.projectSection}
            style={{ marginLeft: "64px" }}
          >
            {projectId && tier === TIERS.FREE ? (
              // FREE tier: Always show project name with upgrade badge (single project enforced by backend)
              <Group gap="xs" className={classes.projectInfo}>
                <IconFolder size={18} style={{ color: "#0ba09a" }} />
                <Text className={classes.projectName}>{projectName}</Text>
                <Badge
                  variant="light"
                  color="teal"
                  size="sm"
                  className={classes.upgradeBadge}
                  onClick={() => navigate("/pricing")}
                >
                  Free · Upgrade
                </Badge>
              </Group>
            ) : projectId && projects.length > 1 ? (
              // ENTERPRISE tier with multiple projects - show selector
              <Select
                leftSection={<IconFolder size={18} />}
                placeholder="Select project"
                data={projects.map((p) => ({
                  value: p.projectId,
                  label: p.name,
                }))}
                value={projectId}
                onChange={handleProjectSwitch}
                className={classes.projectDropdown}
                comboboxProps={{ withinPortal: true }}
              />
            ) : projectId ? (
              // ENTERPRISE tier with single project - show project name only
              <Group gap="xs" className={classes.projectInfo}>
                <IconFolder size={18} style={{ color: "#0ba09a" }} />
                <Text className={classes.projectName}>{projectName}</Text>
              </Group>
            ) : null}
          </Box>
        </Box>
      </AppShell.Header>
    </>
  );
}
