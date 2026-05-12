# Plans — Manifest

Index of every deep-dive handbook. Each `<component>/` folder is a self-contained rebuild kit: if the source disappeared, an engineer (or LLM) could regenerate the component from these files alone.

**Routing rule for agents:** never load a whole plan folder. Start at `<component>/index.md`, then pull only the sub-files you need for the current task.

| Component | Plan index | Sub-files | Status |
|---|---|---|---|
| backend-server | [index](./backend-server/index.md) | 6 core + 19 domains = 25 | full |
| pulse-alerts-cron | [index](./pulse-alerts-cron/index.md) | 3 core + 3 delivery = 6 | full |
| session-capture-service | [index](./session-capture-service/index.md) | 3 core + 2 ops = 5 | full |
| session-replay-ingestion | [index](./session-replay-ingestion/index.md) | 3 core | full |
| heatmap-screenshot-ingestion | [index](./heatmap-screenshot-ingestion/index.md) | 3 core | full |
| spark-jobs | [index](./spark-jobs/index.md) | 3 jobs | full |
| pulse-db | [index](./pulse-db/index.md) | 5 mysql + 4 clickhouse = 9 | full |
| pulse-ingestion | [index](./pulse-ingestion/index.md) | 3 collector | full |
| pulse-ui | [index](./pulse-ui/index.md) | 5 core + 3 shared + 26 screens = 34 | full |
| pulse-web-otel | [index](./pulse-web-otel/index.md) | 8 core + 7 instrumentations + 5 pipeline + 3 integrations = 23 | full |
| pulse-android-otel | [index](./pulse-android-otel/index.md) | 4 core + 7 instrumentations = 11 | full |
| pulse-ios-otel | [index](./pulse-ios-otel/index.md) | 4 core + 5 instrumentations = 9 | full |
| pulse-react-native-otel | [index](./pulse-react-native-otel/index.md) | 3 core + 5 instrumentations = 8 | full |
| pulse-mcp | [index](./pulse-mcp/index.md) | 5 tools | full |
| pulse-ai | [index](./pulse-ai/index.md) | 3 core + 3 tools = 6 | full |
| vector | [index](./vector/index.md) | 3 config | full |
| deploy | [index](./deploy/index.md) | 3 compose + 3 scripts = 6 | full |

Each handbook file follows the same template (Purpose, Source location, Public surface, Internal design, Dependencies, Data contracts, Tests, History/decisions, Rebuild recipe) and is capped at 400 lines.
