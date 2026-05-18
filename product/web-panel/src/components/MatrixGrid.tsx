import { Children, isValidElement, type ReactNode } from 'react';
import styles from './MatrixGrid.module.css';

interface Props {
  children: ReactNode;
}

interface CellData {
  content: ReactNode;
  className: string;
}

export default function MatrixGrid({ children }: Props) {
  const { headers, rows } = parseTable(children);
  if (headers.length === 0 || rows.length === 0) {
    // Fall back to a normal table when parsing fails
    return <table className={styles.fallback}>{children}</table>;
  }

  return (
    <div className={styles.matrix} role="table" aria-label="Progress matrix">
      <div className={styles.headRow}>
        <div className={styles.corner} />
        {headers.slice(1).map((h, i) => (
          <div key={i} className={styles.headCell}>
            {h}
          </div>
        ))}
      </div>
      {rows.map((row, i) => (
        <div key={i} className={styles.bodyRow}>
          <div className={styles.rowLabel}>{row[0]?.content}</div>
          {row.slice(1).map((cell, j) => (
            <div key={j} className={`${styles.cell} ${classFor(cell.className)}`}>
              <div className={styles.cellContent}>{cell.content}</div>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

function parseTable(children: ReactNode): { headers: ReactNode[]; rows: CellData[][] } {
  const headers: ReactNode[] = [];
  const rows: CellData[][] = [];

  Children.forEach(children, (section) => {
    if (!isValidElement(section)) return;
    const sectionType = (section.type as string) || '';
    if (sectionType === 'thead') {
      Children.forEach((section.props as { children?: ReactNode }).children, (tr) => {
        if (!isValidElement(tr)) return;
        Children.forEach((tr.props as { children?: ReactNode }).children, (th) => {
          if (!isValidElement(th)) return;
          headers.push((th.props as { children?: ReactNode }).children);
        });
      });
    } else if (sectionType === 'tbody') {
      Children.forEach((section.props as { children?: ReactNode }).children, (tr) => {
        if (!isValidElement(tr)) return;
        const cells: CellData[] = [];
        Children.forEach((tr.props as { children?: ReactNode }).children, (td) => {
          if (!isValidElement(td)) return;
          const props = td.props as { children?: ReactNode; className?: string };
          cells.push({
            content: props.children,
            className: props.className || '',
          });
        });
        rows.push(cells);
      });
    }
  });

  return { headers, rows };
}

function classFor(className: string): string {
  if (className.includes('status-built')) return styles.cellBuilt;
  if (className.includes('status-wip')) return styles.cellWip;
  if (className.includes('status-notbuilt')) return styles.cellNotbuilt;
  return '';
}
