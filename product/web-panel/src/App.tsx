import { useEffect, useMemo, useState } from 'react';
import { docs, findDoc } from './lib/docs';
import { useHashRoute } from './hooks/useHashRoute';
import { useTheme } from './hooks/useTheme';
import Sidebar from './components/Sidebar';
import DocViewer from './components/DocViewer';
import styles from './App.module.css';

export default function App() {
  const { route, navigate } = useHashRoute();
  const [searchQuery, setSearchQuery] = useState('');
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { theme, toggle: toggleTheme } = useTheme();

  const activeDoc = useMemo(() => {
    if (route) return findDoc(route) ?? docs[0];
    return docs[0];
  }, [route]);

  useEffect(() => {
    if (!route && docs.length > 0) {
      navigate(docs[0].path);
    }
  }, [route, navigate]);

  const handleSelect = (slug: string) => {
    navigate(slug);
    setSidebarOpen(false);
    setSearchQuery('');
  };

  return (
    <div className={styles.layout}>
      <button
        className={styles.menuBtn}
        onClick={() => setSidebarOpen((v) => !v)}
        aria-label="Toggle menu"
      >
        ☰
      </button>

      <Sidebar
        docs={docs}
        activeId={activeDoc?.id}
        onSelect={handleSelect}
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        theme={theme}
        onToggleTheme={toggleTheme}
      />

      <main className={styles.main}>
        {activeDoc ? (
          <DocViewer doc={activeDoc} />
        ) : (
          <div className={styles.empty}>
            <h2>No documents yet</h2>
            <p>
              Drop a <code>.md</code> file into <code>frameworks/</code> or <code>prds/</code> and it
              will appear here.
            </p>
          </div>
        )}
      </main>
    </div>
  );
}
