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
import { TIERS } from "../../constants/Tiers";
import {
  IconHelp,
  IconLogout,
  IconMessageCircle,
  IconUserCircle,
  IconSettings,
  IconUsers,
  IconFolder,
  IconCreditCard,
} from "@tabler/icons-react";
import Cookies from "js-cookie";
import { useRef, useState } from "react";
import { getCookies } from "../../helpers/cookies";
import { isGcpMultiTenantEnabled } from "../../helpers/gcpAuth";
import { useTenantContext, useProjectContext } from "../../contexts";
import { usePermissions } from "../../hooks";
import { performLogout } from "../../helpers/logout";
import { ConfirmationModal } from "../ConfirmationModal";

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
  const { projectId: contextProjectId, clearProject } = useProjectContext();
  const { tenantId, tenantName, tier, clearTenant } = useTenantContext();
  const permissions = usePermissions();
  const [logoutModalOpened, setLogoutModalOpened] = useState(false);

  // Show nav items only on project dashboard pages (not on org pages or onboarding)
  const isProjectDashboard =
    pathname.startsWith("/projects/") && !pathname.includes("/onboarding");

  const handleAllProjectsClick = () => {
    clearProject();
    if (tenantId) {
      navigate(`/${tenantId}/projects`);
    }
  };

  function onItemClick(routeTo: string) {
    // Transform flat routes to project-scoped routes
    if (
      contextProjectId &&
      !routeTo.startsWith("/organization") &&
      !routeTo.startsWith("/projects/")
    ) {
      const projectScopedRoute = `${ROUTES.PROJECT_DASHBOARD.basePath.replace(":projectId", contextProjectId)}${routeTo}`;
      navigate(projectScopedRoute);
    } else {
      navigate(routeTo);
    }
  }

  const isActive = (path: string) => {
    const decodedRouteName = decodeURIComponent(pathname);

    // For project-scoped routes, check the part after /projects/:projectId
    if (decodedRouteName.startsWith("/projects/")) {
      const projectPathParts = decodedRouteName.split("/").slice(3); // Skip '', 'projects', projectId
      const projectPath = "/" + projectPathParts.join("/");
      const basePath = "/" + path.split("/")[1];
      return projectPath.startsWith(basePath);
    }

    // For other routes, use the old logic
    const base = path.split("/")[1];
    const baseMatch = decodedRouteName.split("/")[1];
    return base === baseMatch;
  };

  const onLogoClick = () => {
    if (contextProjectId) {
      navigate(
        ROUTES.PROJECT_DASHBOARD.basePath.replace(
          ":projectId",
          contextProjectId,
        ),
      );
    } else if (tenantId) {
      navigate(
        ROUTES.ORGANIZATION_PROJECTS.basePath.replace(
          ":organizationId",
          tenantId,
        ),
      );
    }
  };

  const onLogoutClick = async () => {
    setLogoutModalOpened(false);

    // Clear all React contexts explicitly
    clearProject();
    clearTenant();

    // Perform logout (clears cookies, sessionStorage, and signs out)
    await performLogout();

    // Navigate to login
    navigate(ROUTES.LOGIN.basePath);
  };

  const gcpMultiTenantEnabled = isGcpMultiTenantEnabled();
  const currentTenantId = tenantId || getCookies(COOKIES_KEY.TENANT_ID);

  return (
    <AppShell.Navbar className={classes.navbarContainer}>
      <AppShell.Section
        className={classes.navbarHeader}
        style={{
          height: 60,
          display: "flex",
          alignItems: "center",
          padding: "0 16px",
        }}
      >
        <Box
          className={classes.logoSection}
          style={{
            width: "100%",
            justifyContent: opened ? "flex-start" : "center",
          }}
        >
          {opened ? (
            <Box className={classes.logoExpanded} onClick={onLogoClick}>
              <Image
                src={(process.env.PUBLIC_URL || "") + "/logo.svg"}
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
                src={(process.env.PUBLIC_URL || "") + "/logo.svg"}
                radius="md"
                className={classes.logoCollapsed}
                onClick={onLogoClick}
                alt=""
              />
            </Tooltip>
          )}
        </Box>
      </AppShell.Section>

      {/* Only show navigation items on project dashboard pages */}
      {isProjectDashboard && (
        <>
          <AppShell.Section
            grow
            my="xl"
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
        </>
      )}

      {/* Bottom Section: Menu Button */}
      <AppShell.Section
        className={classes.menuSectionContainer}
        style={{ paddingBottom: 16 }}
      >
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
              <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
                Organization
              </Text>

              {tier === TIERS.ENTERPRISE && (
                <Box
                  className={classes.menuItem}
                  onClick={handleAllProjectsClick}
                  style={{ cursor: "pointer" }}
                >
                  <Group gap="sm">
                    <IconFolder size={20} style={{ color: "#0ba09a" }} />
                    <Box>
                      <Text size="sm" fw={500}>
                        Projects
                      </Text>
                      <Text size="xs" c="dimmed">
                        View all projects
                      </Text>
                    </Box>
                  </Group>
                </Box>
              )}

              <Box
                className={classes.menuItem}
                onClick={() =>
                  navigate(
                    ROUTES.ORGANIZATION_MEMBERS.basePath.replace(
                      ":organizationId",
                      tenantId || "",
                    ),
                  )
                }
                style={{ cursor: "pointer" }}
              >
                <Group gap="sm">
                  <IconUsers size={20} style={{ color: "#0ba09a" }} />
                  <Box>
                    <Text size="sm" fw={500}>
                      Members
                    </Text>
                    <Text size="xs" c="dimmed">
                      Team management
                    </Text>
                  </Box>
                </Group>
              </Box>

              <Box
                className={classes.menuItem}
                onClick={() => navigate(ROUTES.PRICING.basePath)}
                style={{ cursor: "pointer" }}
              >
                <Group gap="sm">
                  <IconCreditCard size={20} style={{ color: "#0ba09a" }} />
                  <Box>
                    <Text size="sm" fw={500}>
                      Pricing & Plans
                    </Text>
                    <Text size="xs" c="dimmed">
                      {tier === TIERS.ENTERPRISE
                        ? "View your plan"
                        : "Upgrade to Enterprise"}
                    </Text>
                  </Box>
                </Group>
              </Box>

              <Divider />

              {/* Project Section - Only show if a project is selected */}
              {contextProjectId && (
                <>
                  <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
                    Current Project
                  </Text>

                  {/* Settings Link */}
                  {permissions.canManageProjectSettings && (
                    <Box
                      className={classes.menuItem}
                      onClick={() => {
                        if (contextProjectId) {
                          navigate(
                            `${ROUTES.PROJECT_DASHBOARD.basePath.replace(":projectId", contextProjectId)}${ROUTES.PROJECT_SETTINGS.basePath}`,
                          );
                        }
                      }}
                      style={{ cursor: "pointer" }}
                    >
                      <Group gap="sm">
                        <IconSettings size={20} style={{ color: "#0ba09a" }} />
                        <Box>
                          <Text size="sm" fw={500}>
                            Settings
                          </Text>
                          <Text size="xs" c="dimmed">
                            API Keys, Team & Configuration
                          </Text>
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
                onClick={() => setLogoutModalOpened(true)}
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

      {/* Logout Confirmation Modal */}
      <ConfirmationModal
        opened={logoutModalOpened}
        onClose={() => setLogoutModalOpened(false)}
        onConfirm={onLogoutClick}
        title="Confirm Logout"
        message="Are you sure you want to log out? You will need to sign in again to access your account."
        confirmLabel="Logout"
        confirmColor="red"
        severity="warning"
      />
    </AppShell.Navbar>
  );
}
