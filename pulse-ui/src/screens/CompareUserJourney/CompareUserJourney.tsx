import { Title } from "@mantine/core";
import classes from "./CompareUserJourney.module.css";
import { COMMON_CONSTANTS } from "../../constants";

export function CompareUserJourney() {
  return (
    <div className={classes.compareUserJourneyContainer}>
      <Title order={3}>{COMMON_CONSTANTS.COMING_SOON}</Title>
    </div>
  );
}
