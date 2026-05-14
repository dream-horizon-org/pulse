import type { Doc } from '../types';
import { isPlaceholder } from '../lib/frontmatter';
import { estimateReadingMinutes } from '../lib/reading-time';
import styles from './MetaStrip.module.css';

interface Props {
  doc: Doc;
}

const STATUS_LABELS: Record<string, { label: string; cls: string }> = {
  draft: { label: 'Draft', cls: 'statusDraft' },
  'in-review': { label: 'In Review', cls: 'statusReview' },
  approved: { label: 'Approved', cls: 'statusApproved' },
  'in-execution': { label: 'In Execution', cls: 'statusExecution' },
  live: { label: 'Live', cls: 'statusLive' },
};

export default function MetaStrip({ doc }: Props) {
  const fm = doc.frontmatter;
  const items: Array<{ label: string; value: React.ReactNode; cls?: string }> = [];

  if (fm.status && !isPlaceholder(fm.status)) {
    const meta = STATUS_LABELS[fm.status.toLowerCase()];
    items.push({
      label: 'Status',
      value: meta?.label ?? fm.status,
      cls: meta?.cls,
    });
  }
  if (fm.owner && !isPlaceholder(fm.owner)) {
    items.push({ label: 'Owner', value: fm.owner });
  }
  if (fm['last-edited'] && !isPlaceholder(fm['last-edited'])) {
    items.push({ label: 'Last edited', value: fm['last-edited'] });
  }
  if (fm.tracker && !isPlaceholder(fm.tracker)) {
    items.push({
      label: 'Tracker',
      value: (
        <a href={fm.tracker} target="_blank" rel="noreferrer">
          {shorten(fm.tracker)}
        </a>
      ),
    });
  }

  const minutes = estimateReadingMinutes(doc.content);
  if (minutes > 0) {
    items.push({ label: 'Read time', value: `${minutes} min` });
  }

  if (items.length === 0) return null;

  return (
    <div className={styles.strip}>
      {items.map((item, i) => (
        <div key={i} className={styles.cell}>
          <span className={styles.label}>{item.label}</span>
          <span className={`${styles.value} ${item.cls ? styles[item.cls] : ''}`}>{item.value}</span>
        </div>
      ))}
    </div>
  );
}

function shorten(url: string): string {
  try {
    const u = new URL(url);
    return `${u.hostname.replace(/^www\./, '')}${u.pathname}`;
  } catch {
    return url;
  }
}
