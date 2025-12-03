import { TopInteractionsHealthProps } from "./TopInteractionsHealth.interface";
import classes from "./TopInteractionsHealth.module.css";
import { InteractionCard } from "../../../CriticalInteractionList/components/InteractionCard";
import { Button } from "@mantine/core";
import { IconArrowRight } from "@tabler/icons-react";
import { useMemo } from "react";
import dayjs from "dayjs";
import { useGetTopInteractionsHealthData } from "../../../../hooks/useGetTopInteractionsHealthData";

export function TopInteractionsHealth({
  onViewAll,
  onCardClick,
}: TopInteractionsHealthProps) {
  const { startTime, endTime } = useMemo(() => {
    return {
      startTime: dayjs()
        .utc()
        .endOf("day")
        .subtract(6, "days")
        .startOf("day")
        .toISOString(),
      endTime: dayjs().utc().endOf("day").toISOString(),
    };
  }, []);

  const { data: topInteractionsData } = useGetTopInteractionsHealthData({
    startTime,
    endTime,
      limit: 4,
  });

  return (
    <div>
      <div className={classes.headerContainer}>
        <h2 className={classes.sectionTitle}>Top Interactions Health</h2>
        <Button
          variant="subtle"
          rightSection={<IconArrowRight size={16} />}
          onClick={onViewAll}
          className={classes.viewAllButton}
        >
          View All
        </Button>
      </div>
      <div className={classes.interactionsGrid}>
        {topInteractionsData.map((interaction) => (
          <InteractionCard
            key={interaction.id}
            interactionName={interaction.interactionName}
            apdexScore={interaction.apdex}
            errorRateValue={interaction.errorRate}
            p50Latency={interaction.p50}
            poorUserPercentage={interaction.poorUserPercentage}
            onClick={() =>
              onCardClick({
                id: interaction.id,
                name: interaction.interactionName,
              })
            }
          />
        ))}
      </div>
    </div>
  );
}
