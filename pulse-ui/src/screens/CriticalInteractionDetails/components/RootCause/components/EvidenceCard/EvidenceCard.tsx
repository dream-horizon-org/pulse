import { Button } from "@mantine/core";
import { Link } from "react-router-dom";
import type { EvidenceCardProps } from "./EvidenceCard.interface";
import classes from "./EvidenceCard.module.css";

const TYPE_LABELS: Record<EvidenceCardProps["type"], string> = {
  "session-replay": "Session replay",
  funnel: "Funnel",
  journey: "Journey",
  heatmap: "Heatmap",
};

const TYPE_LABEL_COLOR_CLASS: Record<EvidenceCardProps["type"], string> = {
  "session-replay": classes.typeLabelSessionReplay,
  funnel: classes.typeLabelFunnel,
  journey: classes.typeLabelJourney,
  heatmap: classes.typeLabelHeatmap,
};

function buildAriaLabel(
  type: EvidenceCardProps["type"],
  name: string,
  subtitle: string | undefined,
  detail: string | undefined,
): string {
  const parts = [TYPE_LABELS[type], name, subtitle, detail].filter(
    (p): p is string => Boolean(p && p.trim()),
  );
  return parts.join(". ");
}

export const EvidenceCard = ({
  type,
  name,
  timestamp,
  subtitle,
  detail,
  href,
}: EvidenceCardProps) => {
  const summary = buildAriaLabel(type, name, subtitle, detail);

  return (
    <article className={classes.card} aria-label={summary}>
      <div className={classes.cardBody}>
        <div className={classes.topRow}>
          <span
            className={`${classes.typeLabel} ${TYPE_LABEL_COLOR_CLASS[type]}`}
          >
            {TYPE_LABELS[type]}
          </span>
          {timestamp && <span className={classes.timestamp}>{timestamp}</span>}
        </div>
        <div className={classes.name}>{name}</div>
        {subtitle && <div className={classes.subtitle}>{subtitle}</div>}
        {detail && <p className={classes.detail}>{detail}</p>}
      </div>
      <Button
        component={Link}
        to={href}
        variant="light"
        color="teal"
        size="xs"
        fullWidth
        className={classes.viewDetail}
        aria-label={`View Detail — ${TYPE_LABELS[type]}: ${name}`}
      >
        View Detail
      </Button>
    </article>
  );
};
