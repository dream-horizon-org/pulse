import { AppShell, Text } from "@mantine/core";
import classes from "./Footer.module.css";
import { FOOTER_CONSTANTS } from "../../constants";

export function Footer() {
  return (
    <AppShell.Footer
      className={classes.footerContainer}
      styles={{
        footer: {
          zIndex: 1000,
        },
      }}
    >
      <Text fz="md">{FOOTER_CONSTANTS.FOOTER_MESSAGE}</Text>
    </AppShell.Footer>
  );
}
