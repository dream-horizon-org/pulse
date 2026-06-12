import { MantineTheme } from "@mantine/core";
import { INTERACTION_STATUS } from "../../constants";

export const getBadgeColorClass = (status: string, theme: MantineTheme) => {
  switch (status) {
    case INTERACTION_STATUS.STOPPED:
      return theme.colors.red[9];
    case INTERACTION_STATUS.RUNNING:
      return theme.colors.green[9];
    default:
      break;
  }
  return theme.colors.primary[6];
};
