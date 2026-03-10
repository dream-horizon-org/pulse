import classes from "./Navbar.module.css";
import {
  Anchor,
  AppShell,
  ScrollArea,
  Text,
  Tooltip,
  Box,
  Image,
  Popover,
  Avatar,
  Button,
  Stack,
  Group,
  Divider,
  ActionIcon,
} from "@mantine/core";
import { useLocation, useNavigate } from "react-router-dom";
import {
  COMMON_CONSTANTS,
  COOKIES_KEY,
  FOOTER_CONSTANTS,
  HEADER_CONSTANTS,
  MULTI_TENANT_CONSTANTS,
  NAVBAR_CONSTANTS,
  NAVBAR_ITEMS,
  ROUTES,
} from "../../constants";
import {
  IconHelp,
  IconLogout,
  IconMessageCircle,
  IconUserCircle,
  IconSettings,
  IconUsers,
  IconFolder,
} from "@tabler/icons-react";
import Cookies from "js-cookie";
import { useRef } from "react";
import { getCookies } from "../../helpers/cookies";
import { isGcpMultiTenantEnabled } from "../../helpers/gcpAuth";
import { useTenantContext, useProjectContext } from "../../contexts";
import { usePermissions } from "../../hooks";
import { performLogout } from "../../helpers/logout";

export function Navbar({
  toggle,
  opened,
}: {
  toggle: () => void;
  opened: boolean;
}) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const userProfilePicture = useRef<string>(
    Cookies.get(COOKIES_KEY.USER_PICTURE) ?? "",
  );
  const { projectId: contextProjectId, clearProject, plan } = useProjectContext();
  const { tenantId, tenantName, clearTenant } = useTenantContext();
  const permissions = usePermissions();

  const handleAllProjectsClick = () => {
    console.log('[Navbar] Clearing project context and navigating to project selection');
    clearProject();
    navigate(ROUTES.PROJECT_SELECTION.basePath);
  };

  function onItemClick(routeTo: string) {
    // Transform flat routes to project-scoped routes
    if (contextProjectId && !routeTo.startsWith('/organization') && !routeTo.startsWith('/projects/')) {
      const projectScopedRoute = `/projects/${contextProjectId}${routeTo}`;
      navigate(projectScopedRoute);
    } else {
      navigate(routeTo);
    }
  }

  const isActive = (path: string) => {
    const decodedRouteName = decodeURIComponent(pathname);
    
    // For project-scoped routes, check the part after /projects/:projectId
    if (decodedRouteName.startsWith('/projects/')) {
      const projectPathParts = decodedRouteName.split('/').slice(3); // Skip '', 'projects', projectId
      const projectPath = '/' + projectPathParts.join('/');
      const basePath = '/' + path.split('/')[1];
      return projectPath.startsWith(basePath);
    }
    
    // For other routes, use the old logic
    const base = path.split("/")[1];
    const baseMatch = decodedRouteName.split("/")[1];
    return base === baseMatch;
  };

  const onLogoClick = () => {
    if (contextProjectId) {
      navigate(`/projects/${contextProjectId}`);
    } else {
      navigate(ROUTES.PROJECT_SELECTION.basePath);
    }
  };

  const onLogoutClick = async () => {
    // Clear all React contexts explicitly
    clearProject();
    clearTenant();
    console.log('[Navbar] Cleared project and tenant contexts');
    
    // Perform logout (clears cookies, sessionStorage, and signs out)
    await performLogout();
    
    // Navigate to login
    navigate(ROUTES.LOGIN.basePath);
  };

  const gcpMultiTenantEnabled = isGcpMultiTenantEnabled();
  const currentTenantId = tenantId || getCookies(COOKIES_KEY.TENANT_ID);

  return (
    <AppShell.Navbar pt="md" pb="md" className={classes.navbarContainer}>
      <AppShell.Section className={classes.navbarHeader}>
        <Box className={classes.logoSection}>
          {opened ? (
            <Box className={classes.logoExpanded} onClick={onLogoClick}>
              <Image
                src={(process.env.PUBLIC_URL || '') + "/logo.svg"}
                radius="md"
                className={classes.logo}
                alt=""
              />
              <Text className={classes.appName}>
                {COMMON_CONSTANTS.APP_NAME}
              </Text>
            </Box>
          ) : (
            <Tooltip
              label={COMMON_CONSTANTS.APP_NAME}
              position="right"
              withArrow
            >
              <Image
                src={(process.env.PUBLIC_URL || '') + "/logo.svg"}
                radius="md"
                className={classes.logoCollapsed}
                onClick={onLogoClick}
                alt=""
              />
            </Tooltip>
          )}
          {/* {opened ? (
            <Tooltip label={TOOLTIP_LABLES.CLOSE_NAVBAR}>
              <IconCircleChevronLeft
                onClick={toggle}
                className={classes.toggleIcon}
              />
            </Tooltip>
          ) : (
            <Tooltip label={TOOLTIP_LABLES.OPEN_NAVBAR}>
              <IconCircleChevronRight
                onClick={toggle}
                className={classes.toggleIcon}
              />
            </Tooltip>
          )} */}
        </Box>
      </AppShell.Section>

      <Divider my="sm" />
      <AppShell.Section
        grow
        my="md"
        component={ScrollArea}
        style={{
          width: "100%",
        }}
      >
        {NAVBAR_ITEMS.map((item) => {
          const NavbarIcon = item.icon;
          const active = isActive(item.routeTo);

          const navItem = (
            <Box
              key={item.tabName}
              className={`${classes.navbarItem} ${active ? classes.navbarItemActive : ""}`}
              onClick={() => onItemClick(item.routeTo)}
              style={{
                justifyContent: opened ? "flex-start" : "center",
                padding: opened ? "12px" : "12px 8px",
              }}
            >
              <NavbarIcon
                size={item.iconSize}
                className={classes.navbarIcon}
                style={{ color: active ? "#0ba09a" : "#64748b" }}
              />
              {opened && (
                <Text className={classes.navbarText}>{item.tabName}</Text>
              )}
            </Box>
          );

          // Show tooltip only when collapsed
          if (!opened) {
            return (
              <Tooltip
                key={item.tabName}
                label={item.tabName}
                position="right"
                withArrow
              >
                {navItem}
              </Tooltip>
            );
          }

          return navItem;
        })}
      </AppShell.Section>

      {/* Bottom Section: Menu Button */}
      <AppShell.Section className={classes.menuSectionContainer}>
        <Divider my="sm" />

        <Popover width={280} position="right-end" withArrow shadow="md">
          <Popover.Target>
            {opened ? (
              <Button
                variant="light"
                color="teal"
                fullWidth
                leftSection={<IconUserCircle size={20} />}
                className={classes.menuButton}
              >
                More
              </Button>
            ) : (
              <Tooltip label="More" position="right" withArrow>
                <ActionIcon
                  variant="light"
                  color="teal"
                  size="lg"
                  className={classes.menuButtonCollapsed}
                >
                  <IconUserCircle size={22} />
                </ActionIcon>
              </Tooltip>
            )}
          </Popover.Target>
          <Popover.Dropdown p="md" style={{ width: 350 }}>
            <Stack gap="md">
              {/* User Profile Section */}
              <Box>
                <Group gap="sm" mb="xs">
                  <Avatar
                    size="md"
                    radius="md"
                    src={userProfilePicture.current}
                  />
                  <Box style={{ flex: 1, minWidth: 0 }}>
                    <Text size="sm" fw={600} truncate>
                      {getCookies(COOKIES_KEY.USER_NAME)}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {getCookies(COOKIES_KEY.USER_EMAIL)}
                    </Text>
                    {gcpMultiTenantEnabled &&
                      currentTenantId &&
                      currentTenantId !== "undefined" && (
                        <Text size="xs" c="dimmed" mt={4}>
                          {MULTI_TENANT_CONSTANTS.CURRENT_TENANT_LABEL}:{" "}
                          {tenantName || currentTenantId}
                        </Text>
                    )}
                  </Box>
                </Group>
              </Box>

              <Divider />

              {/* Organization Section */}
              <Text size="xs" c="dimmed" tt="uppercase" fw={700}>Organization</Text>
              
              {plan === 'enterprise' && (
                <Box
                  className={classes.menuItem}
                  onClick={handleAllProjectsClick}
                  style={{ cursor: 'pointer' }}
                >
                  <Group gap="sm">
                    <IconFolder size={20} style={{ color: "#0ba09a" }} />
                    <Box>
                      <Text size="sm" fw={500}>All Projects</Text>
                      <Text size="xs" c="dimmed">Switch projects</Text>
                    </Box>
                  </Group>
                </Box>
              )}

              <Box
                className={classes.menuItem}
                onClick={() => navigate(ROUTES.ORGANIZATION_MEMBERS.basePath)}
                style={{ cursor: 'pointer' }}
              >
                <Group gap="sm">
                  <IconUsers size={20} style={{ color: "#0ba09a" }} />
                  <Box>
                    <Text size="sm" fw={500}>Members</Text>
                    <Text size="xs" c="dimmed">Team management</Text>
                  </Box>
                </Group>
              </Box>

              <Divider />

              {/* Project Section - Only show if a project is selected */}
              {contextProjectId && (
                <>
                  <Text size="xs" c="dimmed" tt="uppercase" fw={700}>Current Project</Text>

                  {/* Settings Link */}
                  {permissions.canManageProjectSettings && (
                    <Box
                      className={classes.menuItem}
                      onClick={() => navigate(`/projects/${contextProjectId}/settings`)}
                      style={{ cursor: 'pointer' }}
                    >
                      <Group gap="sm">
                        <IconSettings size={20} style={{ color: "#0ba09a" }} />
                        <Box>
                          <Text size="sm" fw={500}>Settings</Text>
                          <Text size="xs" c="dimmed">API Keys, Team & Configuration</Text>
                        </Box>
                      </Group>
                    </Box>
                  )}
                </>
              )}

              <Divider />
              {/* Help Link */}
              <Anchor
                href={NAVBAR_CONSTANTS.HELP_LINK}
                target="_blank"
                underline="never"
                className={classes.menuItem}
              >
                <Group gap="sm">
                  <IconHelp size={20} style={{ color: "#0ba09a" }} />
                  <Text size="sm">{NAVBAR_CONSTANTS.HELP_BAR_TEXT}</Text>
                </Group>
              </Anchor>

              {/* Footer Message - Discord Link */}
              <Anchor
                href={FOOTER_CONSTANTS.DISCORD_LINK}
                target="_blank"
                underline="never"
                className={classes.menuFooterMessage}
              >
                <Group gap="xs" className={classes.menuFooterMessageContent}>
                  <IconMessageCircle
                    size={18}
                    style={{ color: "#0ba09a", flexShrink: 0, marginTop: 2 }}
                  />
                  <Text size="xs" c="dimmed" style={{ lineHeight: 1.4 }}>
                    {FOOTER_CONSTANTS.FOOTER_MESSAGE}
                  </Text>
                </Group>
              </Anchor>

              <Divider />

              {/* Logout Button */}
              <Button
                leftSection={<IconLogout size={18} />}
                onClick={onLogoutClick}
                variant="light"
                color="red"
                size="sm"
                fullWidth
              >
                {HEADER_CONSTANTS.LOGOUT_TEXT}
              </Button>
            </Stack>
          </Popover.Dropdown>
        </Popover>
      </AppShell.Section>
    </AppShell.Navbar>
  );
}
