import type { Heading } from '../types';
import styles from './Toc.module.css';

interface Props {
  headings: Heading[];
}

export default function Toc({ headings }: Props) {
  // Skip the document's H1 (title) — only show H2 and H3 in the on-page TOC
  const items = headings.filter((h) => h.level >= 2);
  if (items.length === 0) return null;

  const handleClick = (e: React.MouseEvent<HTMLAnchorElement>, id: string) => {
    e.preventDefault();
    const el = document.getElementById(id);
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  return (
    <aside className={styles.toc}>
      <div className={styles.label}>On this page</div>
      <ul className={styles.list}>
        {items.map((h, i) => (
          <li key={i} className={styles[`level${h.level}` as 'level2' | 'level3']}>
            <a href={`#${h.id}`} onClick={(e) => handleClick(e, h.id)}>
              {h.text}
            </a>
          </li>
        ))}
      </ul>
    </aside>
  );
}
