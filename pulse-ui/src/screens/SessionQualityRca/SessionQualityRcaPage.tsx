import { Box } from "@mantine/core";
import { useMemo } from "react";
import { useParams } from "react-router-dom";
import { SessionQualityRca } from "./SessionQualityRca";
import classes from "./SessionQualityRcaPage.module.css";

const LOOKBACK_DAYS = 6;

export function SessionQualityRcaPage() {
  const { projectId } = useParams<{ projectId: string }>();

  const { date, asOfIso } = useMemo(() => {
    const now = new Date();
    const anchor = new Date(now);
    anchor.setDate(anchor.getDate() - LOOKBACK_DAYS);
    return { date: anchor.toISOString().slice(0, 10), asOfIso: now.toISOString() };
  }, []);

  return (
    <Box className={classes.page}>
      <SessionQualityRca
        projectId={projectId}
        date={date}
        asOfIso={asOfIso}
      />
    </Box>
  );
}
