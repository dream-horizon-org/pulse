import { EvidenceCard } from "../EvidenceCard";
import type { EvidenceStripProps } from "./EvidenceStrip.interface";
import classes from "./EvidenceStrip.module.css";

export const EvidenceStrip = ({ items }: EvidenceStripProps) => {
  if (items.length === 0) return null;

  return (
    <section className={classes.section} aria-label="Evidence">
      <div className={classes.header}>
        <span className={classes.title}>Evidence</span>
        <span className={classes.badge}>{items.length}</span>
      </div>
      <div className={classes.scrollRow}>
        {items.map((item, idx) => (
          <EvidenceCard key={`${item.type}-${item.href}-${idx}`} {...item} />
        ))}
      </div>
    </section>
  );
};
