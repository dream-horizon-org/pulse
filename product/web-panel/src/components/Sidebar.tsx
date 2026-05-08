import { useMemo } from 'react';
import type { Doc } from '../types';
import type { Theme } from '../hooks/useTheme';
import { searchDocs } from '../lib/search';
import ThemeToggle from './ThemeToggle';
import styles from './Sidebar.module.css';

interface Props {
  docs: Doc[];
  activeId?: string;
  onSelect: (slug: string) => void;
  searchQuery: string;
  onSearchChange: (q: string) => void;
  open: boolean;
  onClose: () => void;
  theme: Theme;
  onToggleTheme: () => void;
}

const GROUP_LABELS: Record<string, string> = {
  frameworks: 'Frameworks',
  prds: 'PRDs',
  root: 'Overview',
};

export default function Sidebar({
  docs,
  activeId,
  onSelect,
  searchQuery,
  onSearchChange,
  open,
  onClose,
  theme,
  onToggleTheme,
}: Props) {
  const grouped = useMemo(() => {
    const result: Record<string, Doc[]> = {};
    for (const doc of docs) {
      if (!result[doc.group]) result[doc.group] = [];
      result[doc.group].push(doc);
    }
    return result;
  }, [docs]);

  const searchResults = useMemo(() => searchDocs(searchQuery), [searchQuery]);
  const isSearching = searchQuery.trim().length > 0;

  return (
    <>
      {open && <div className={styles.scrim} onClick={onClose} />}
      <aside className={`${styles.sidebar} ${open ? styles.open : ''}`}>
        <div className={styles.brand}>
          <div className={styles.brandText}>
            <span className={styles.brandTitle}>Pulse</span>
            <span className={styles.brandSub}>Product</span>
          </div>
          <ThemeToggle theme={theme} onToggle={onToggleTheme} />
        </div>

        <div className={styles.searchBox}>
          <input
            type="search"
            placeholder="Search docs…"
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            className={styles.searchInput}
            autoComplete="off"
          />
          {searchQuery && (
            <button
              className={styles.clearBtn}
              onClick={() => onSearchChange('')}
              aria-label="Clear search"
              type="button"
            >
              ×
            </button>
          )}
        </div>

        <nav className={styles.nav}>
          {isSearching ? (
            <div className={styles.searchSection}>
              <div className={styles.groupLabel}>
                Results <span className={styles.count}>{searchResults.length}</span>
              </div>
              {searchResults.length === 0 ? (
                <div className={styles.empty}>No matches.</div>
              ) : (
                searchResults.map(({ doc, snippet }) => (
                  <button
                    key={doc.id}
                    className={`${styles.navItem} ${activeId === doc.id ? styles.active : ''}`}
                    onClick={() => onSelect(doc.path)}
                    type="button"
                  >
                    <div className={styles.navItemTitle}>{doc.title}</div>
                    <div className={styles.navItemPath}>{doc.id}</div>
                    {snippet && <div className={styles.snippet}>{snippet}</div>}
                  </button>
                ))
              )}
            </div>
          ) : (
            Object.entries(grouped).map(([group, items]) => (
              <div key={group} className={styles.group}>
                <div className={styles.groupLabel}>
                  {GROUP_LABELS[group] ?? group}
                  <span className={styles.count}>{items.length}</span>
                </div>
                {items.map((doc) => (
                  <button
                    key={doc.id}
                    className={`${styles.navItem} ${activeId === doc.id ? styles.active : ''}`}
                    onClick={() => onSelect(doc.path)}
                    type="button"
                  >
                    <div className={styles.navItemTitle}>{doc.title}</div>
                  </button>
                ))}
              </div>
            ))
          )}
        </nav>
      </aside>
    </>
  );
}
