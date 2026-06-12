import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { InteractionDetailsGraphs } from "../../../CriticalInteractionDetails/components/InteractionDetailsMainContent/components/InteractionDetailsGraphs";

dayjs.extend(utc);

export function UserExperienceGraphs() {
  return (
    <InteractionDetailsGraphs
      orientation="horizontal"
      startTime={dayjs().utc().subtract(1, "days").toISOString()}
      endTime={dayjs().utc().toISOString()}
    />
  );
}
