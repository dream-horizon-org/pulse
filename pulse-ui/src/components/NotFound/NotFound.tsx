import { Title, useMantineTheme } from "@mantine/core";
import { IconError404 } from "@tabler/icons-react";
import classes from "./NotFound.module.css";
import { COMMON_CONSTANTS } from "../../constants";

export function NotFound() {
  const theme = useMantineTheme();

  return (
    <div className={classes.pageNotFoundContainer}>
      <IconError404 color={theme.colors.primary[6]} size={100} />
      <div className={classes.pageNotFoundMessage}>
        <Title order={2}>{COMMON_CONSTANTS.PAGE_NOT_FOUND_MESSAGE}</Title>
      </div>
    </div>
  );
}
