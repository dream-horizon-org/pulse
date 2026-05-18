import { Tabs } from "@mantine/core";
import { useLocation, useNavigate, useParams } from "react-router-dom";

const TABS = [
  { value: "sessions", label: "Sessions", path: "/session-replay/sessions" },
  { value: "insights", label: "Insights", path: "/session-replay/insights" },
  { value: "quality-rca", label: "Quality RCA", path: "/session-replay/quality-rca" },
];

export function SessionReplayPageNav() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { projectId } = useParams<{ projectId?: string }>();

  const base = projectId ? `/projects/${projectId}` : "";

  const activeTab =
    TABS.find((t) => pathname.includes(t.path))?.value ?? "sessions";

  function handleChange(value: string | null) {
    const tab = TABS.find((t) => t.value === value);
    if (tab) {
      navigate(`${base}${tab.path}`);
    }
  }

  return (
    <Tabs value={activeTab} onChange={handleChange} mb="lg">
      <Tabs.List>
        {TABS.map((t) => (
          <Tabs.Tab key={t.value} value={t.value}>
            {t.label}
          </Tabs.Tab>
        ))}
      </Tabs.List>
    </Tabs>
  );
}
