import { Grid, Text, Tooltip } from "@mantine/core";
import { UserCategorisationDetailsProps } from "./UserCategorisationDetails.interface";
import classes from "./UserCategorisationDetails.module.css";

export function UserCategorisationDetails({
  highUptime,
  lowUptime,
  midUptime,
}: UserCategorisationDetailsProps) {
  return (
    <div>
      <Text size="md">Interaction categorisation</Text>
      <Grid className={classes.UserCategorisationDetailsContainer}>
        <Tooltip
          label={`<${lowUptime}ms`}
          position="bottom"
          opened
          offset={-5}
          zIndex={10}
          withArrow
          styles={{
            tooltip: {
              backgroundColor: "transparent",
              color: "var(--mantine-color-gray-9)",
              fontSize: "var(--mantine-font-size-xs)",
            },
          }}
        >
          <Grid.Col
            span={3}
            style={{
              backgroundColor: "var(--mantine-color-green-9)",
              fontSize: "var(--mantine-font-size-xs)",
              color: "white",
              justifyContent: "center",
              display: "flex",
              fontWeight: "bold",
            }}
          >
            Excellent
          </Grid.Col>
        </Tooltip>
        <Tooltip
          label={`${lowUptime}-${midUptime}ms`}
          position="top"
          zIndex={10}
          offset={-5}
          opened
          withArrow
          styles={{
            tooltip: {
              backgroundColor: "transparent",
              color: "var(--mantine-color-gray-9)",
              fontSize: "var(--mantine-font-size-xs)",
            },
          }}
        >
          <Grid.Col
            span={3}
            style={{
              backgroundColor: "var(--mantine-color-yellow-3)",
              fontSize: "var(--mantine-font-size-xs)",
              color: "white",
              justifyContent: "center",
              display: "flex",
              fontWeight: "bold",
            }}
          >
            Good
          </Grid.Col>
        </Tooltip>
        <Tooltip
          label={`${midUptime}-${highUptime}ms`}
          position="bottom"
          zIndex={10}
          offset={-5}
          withArrow
          opened
          styles={{
            tooltip: {
              backgroundColor: "transparent",
              color: "var(--mantine-color-gray-9)",
              fontSize: "var(--mantine-font-size-xs)",
            },
          }}
        >
          <Grid.Col
            span={3}
            style={{
              backgroundColor: "var(--mantine-color-orange-6)",
              fontSize: "var(--mantine-font-size-xs)",
              color: "white",
              justifyContent: "center",
              display: "flex",
              fontWeight: "bold",
            }}
          >
            Average
          </Grid.Col>
        </Tooltip>
        <Tooltip
          label={`>${highUptime}ms`}
          position="top"
          withArrow
          offset={-5}
          zIndex={10}
          opened
          styles={{
            tooltip: {
              backgroundColor: "transparent",
              color: "var(--mantine-color-gray-9)",
              fontSize: "var(--mantine-font-size-xs)",
            },
          }}
        >
          <Grid.Col
            span={3}
            style={{
              backgroundColor: "var(--mantine-color-red-9)",
              fontSize: "var(--mantine-font-size-xs)",
              color: "white",
              justifyContent: "center",
              display: "flex",
              fontWeight: "bold",
            }}
          >
            Poor
          </Grid.Col>
        </Tooltip>
      </Grid>
    </div>
  );
}
