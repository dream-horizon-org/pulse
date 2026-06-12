import { Grid } from "@mantine/core";
import { InteractionDetailsGraphs } from "./components/InteractionDetailsGraphs";
import { InteractionDetailsEventsInfo } from "./components/InteractionDetailsEventsInfo";
import { InteractionDetailsMainContentProps } from "./InteractionDetailsMainContentProps.interface";

export function InteractionDetailsMainContent(
  props: InteractionDetailsMainContentProps,
) {
  return (
    <Grid>
      <Grid.Col span={props?.jobDetails ? 8.5 : 12}>
        <InteractionDetailsGraphs {...props} />
      </Grid.Col>
      <Grid.Col span={3.5}>
        <InteractionDetailsEventsInfo jobDetails={props.jobDetails} />
      </Grid.Col>
    </Grid>
  );
}
