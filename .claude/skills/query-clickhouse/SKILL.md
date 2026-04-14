---
name: query-clickhouse
description: Execute a SELECT query against the local ClickHouse instance. Usage: /query-clickhouse <SQL>. Read-only — SELECT only.
allowed-tools: Bash(docker exec *)
---

Execute the provided SQL query against ClickHouse:

```bash
docker exec -it clickhouse clickhouse-client \
  --user pulse_user --password pulse_password \
  --database otel \
  --query "$ARGUMENTS"
```

Rules:
- Only run SELECT queries — refuse INSERT/UPDATE/DELETE/DROP/ALTER
- Always add LIMIT if not present (cap at 100 rows for display)
- Format output as a readable table

If no query is provided, show the available tables:
```sql
SHOW TABLES FROM otel
```
