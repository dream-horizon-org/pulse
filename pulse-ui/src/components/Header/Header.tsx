import {
  AppShell,
  Tooltip,
  Group,
  Text,
  Image,
  Box,
  Popover,
  Avatar,
  Button,
  Stack,
} from "@mantine/core";

import classes from "./Header.module.css";
import { HeaderProps } from "./Header.interface";
import { useNavigate } from "react-router-dom";
import {
  COMMON_CONSTANTS,
  COOKIES_KEY,
  HEADER_CONSTANTS,
  ROUTES,
  TOOLTIP_LABLES,
} from "../../constants";
import {
  IconCircleChevronLeft,
  IconCircleChevronRight,
  IconLogout,
  IconUserScan,
} from "@tabler/icons-react";
import Cookies from "js-cookie";
import { useRef } from "react";
import { googleLogout } from "@react-oauth/google";
import { getCookies, removeAllCookies } from "../../helpers/cookies";

export function Header({ toggle: toogle, opened }: HeaderProps) {
  const navigate = useNavigate();
  const userProfilePicture = useRef<string>(
    Cookies.get(COOKIES_KEY.USER_PICTURE) ?? "",
  );

  const onClick = () => {
    navigate("/");
  };

  const onLogoutClick = () => {
    googleLogout();
    removeAllCookies();
    navigate(ROUTES.LOGIN.basePath);
  };

  return (
    <AppShell.Header>
      <Box className={classes.headerContainer}>
        <Group h="100%" px="md" grow>
          {opened ? (
            <Tooltip label={TOOLTIP_LABLES.CLOSE_NAVBAR}>
              <IconCircleChevronLeft
                onClick={toogle}
                className={classes.cheveronIcon}
              />
            </Tooltip>
          ) : (
            <Tooltip label={TOOLTIP_LABLES.OPEN_NAVBAR}>
              <IconCircleChevronRight
                onClick={toogle}
                className={classes.cheveronIcon}
              />
            </Tooltip>
          )}
          <div className={classes.logoContainer} onClick={onClick}>
            <Image
              src={(process.env.PUBLIC_URL || '') + "/logo.svg"}
              radius="md"
              className={classes.logo}
              alt=""
            />
            <Text fs="lg" fw="bold" ml="sm" className={classes.appName}>
              {COMMON_CONSTANTS.APP_NAME}
            </Text>
          </div>
        </Group>
        <Box className={classes.userInformation}>
          <Popover width={200} position="bottom" withArrow shadow="md">
            <Popover.Target>
              <Avatar
                size={"md"}
                radius={"md"}
                src={userProfilePicture.current}
              />
            </Popover.Target>
            <Popover.Dropdown>
              <Stack justify="space-between" gap="sm">
                <Group className={classes.userName}>
                  <IconUserScan size={22} color="#0ba09a" />
                  <Text>{getCookies(COOKIES_KEY.USER_NAME)}</Text>
                </Group>
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
        </Box>
      </Box>
    </AppShell.Header>
  );
}
