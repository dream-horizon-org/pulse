import { Text, Badge } from "@mantine/core";
import { SessionTimelineEvent } from "../../../../SessionTimeline.interface";
import { formatTimeMs } from "../../../../utils/formatters";
import classes from "./SpanDetails.module.css";

interface SpanDetailsProps {
  span: SessionTimelineEvent;
}

const formatTimestamp = (timestamp: number): string => {
  const date = new Date(timestamp);
  return date.toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
};

export function SpanDetails({ span }: SpanDetailsProps) {
  const duration = span.duration || 0;
  const serviceName =
    span.attributes?.resource?.["service.name"] || "Unknown Service";
  const spanKind = span.attributes?.span?.["span.kind"] || "Unset";
  const statusCode =
    span.attributes?.span?.["http.status_code"] ||
    span.attributes?.span?.["status.code"] ||
    "Unset";

  return (
    <div className={classes.detailsContainer}>
      <div className={classes.detailRow}>
        <Text className={classes.label}>SPAN NAME</Text>
        <Badge className={classes.valuePill} variant="light" color="gray">
          {span.name}
        </Badge>
      </div>
      <div className={classes.detailRow}>
        <Text className={classes.label}>SPAN ID</Text>
        <Badge className={classes.valuePill} variant="light" color="gray">
          {span.id}
        </Badge>
      </div>
      <div className={classes.detailRow}>
        <Text className={classes.label}>START TIME</Text>
        <Badge className={classes.valuePill} variant="light" color="gray">
          {span.absoluteTimestamp
            ? formatTimestamp(span.absoluteTimestamp)
            : "N/A"}
        </Badge>
      </div>
      <div className={classes.detailRow}>
        <Text className={classes.label}>DURATION</Text>
        <Badge className={classes.valuePill} variant="light" color="gray">
          {formatTimeMs(duration)}
        </Badge>
      </div>
      <div className={classes.detailRow}>
        <Text className={classes.label}>SERVICE</Text>
        <Badge className={classes.valuePill} variant="light" color="gray">
          • {serviceName}
        </Badge>
      </div>
      <div className={classes.detailRow}>
        <Text className={classes.label}>SPAN KIND</Text>
        <Badge className={classes.valuePill} variant="light" color="gray">
          {spanKind}
        </Badge>
      </div>
      <div className={classes.detailRow}>
        <Text className={classes.label}>STATUS CODE STRING</Text>
        <Badge className={classes.valuePill} variant="light" color="gray">
          {statusCode}
        </Badge>
      </div>
    </div>
  );
}
