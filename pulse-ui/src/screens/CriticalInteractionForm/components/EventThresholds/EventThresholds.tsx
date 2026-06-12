import { Box, Button, Grid } from "@mantine/core";
import classes from "./EventThreshold.module.css";
import { useState } from "react";
import { UserActionCategories } from "../UserActionCategories";
import { UserCategorisationDetails } from "../../../CriticalInteractionDetails/components/InteractionDetailsMainContent/components/UserCategorisationDetails";
import { CustomEventThresholds } from "../CustomEventThresholds";

type EventThresholdProps = {
  isUpdateFlow: boolean;
  onCreateClick: () => void;
  onBackClick: () => void;
};

export function EventThresholds({
  isUpdateFlow,
  onBackClick,
  onCreateClick,
}: EventThresholdProps) {
  const [lowerThreshold, setLowerThreshold] = useState<number>(0);
  const [midThreshold, setMidThreshold] = useState<number>(0);
  const [upperThreshold, setUpperThreshold] = useState<number>(0);

  const onUserInteractionCategoryClick = (
    low: number,
    mid: number,
    high: number,
  ) => {
    setLowerThreshold(low);
    setMidThreshold(mid);
    setUpperThreshold(high);
  };

  const onCreateButtonClick = () => {
    onCreateClick();
  };

  return (
    <Box className={classes.eventThresholdsContainer}>
      <Grid gutter="xl">
        <Grid.Col span={6}>
          <CustomEventThresholds />
          <Box className={classes.sectionButtons}>
            <Button
              className={classes.sectionButton}
              variant="outline"
              size="md"
              onClick={onBackClick}
            >
              {"Back"}
            </Button>
            <Button
              className={classes.sectionButton}
              variant="filled"
              size="md"
              onClick={onCreateButtonClick}
            >
              {isUpdateFlow ? "Update Interaction" : "Create Interaction"}
            </Button>
          </Box>
        </Grid.Col>
        {
          <Grid.Col span={6} className={classes.userActionCategoriesContainer}>
            <UserActionCategories
              onInteractionCategoryChange={onUserInteractionCategoryClick}
            />
            <Box className={classes.thresoldInputContainer}>
              <UserCategorisationDetails
                lowUptime={lowerThreshold}
                highUptime={upperThreshold}
                midUptime={midThreshold}
              />
            </Box>
          </Grid.Col>
        }
      </Grid>
    </Box>
  );
}
