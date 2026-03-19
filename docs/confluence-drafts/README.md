# Confluence draft sources

Sources used to update Pulse Confluence pages via MCP (`confluence_update_page`). Edit these files first, then re-publish to Confluence when content changes.

**Split:** [Schema Design (4787011590)](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590) holds **all DDL** (MySQL, ClickHouse, S3 layout). [Spark job plan (4782587990)](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4782587990) holds **Spark runtime only** (no duplicated schema).

**Decision doc:** [Funnel & Journey Data Architecture (4775477367)](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4775477367) — options, recommendation, **finalized production approach**; publish with `generate_funnel_data_architecture_decision_storage.py`. The old [finalized-only page (4791042087)](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4791042087) is a **deprecated** redirect.

| Draft file | Confluence page |
|------------|-----------------|
| `funnel-schema-design-page.md` | [Funnel & User Journey Schema Design](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590) — human-readable mirror in repo |
| `generate_funnel_schema_storage.py` | [Schema Design](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590) — **all** MySQL / CH / S3 layout DDL; **use this to publish** (storage + Code macros) |
| `funnel-schema-design-page.confluence-wiki.txt` | Legacy wiki markup reference; MCP `wiki` mode did not render `{code:sql}` reliably in tests |
| `funnel-api-page.md` | [Funnel & User Journey API](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4785078289) |
| `generate_funnel_spark_implementation_storage.py` | [Funnel Spark implementation (job plan)](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4782587990) — **Spark runtime only** (no DDL); publish via **storage** |
| `generate_funnel_data_architecture_decision_storage.py` | [Data Architecture Decision](https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4775477367) — options + **finalized approach** merged here |

### Publishing the schema page (readable SQL blocks)

Converting markdown fences via the API often produces broken code blocks in Cloud. Publish **storage** format instead:

```bash
python3 docs/confluence-drafts/generate_funnel_schema_storage.py > /tmp/funnel-schema-storage.xml
python3 -c "
import json, pathlib
content = pathlib.Path('/tmp/funnel-schema-storage.xml').read_text()
args = {
  'page_id': '4787011590',
  'title': 'Funnel & User Journey Schema Design',
  'content': content,
  'content_format': 'storage',
  'version_comment': 'Regenerate from generate_funnel_schema_storage.py',
  'is_minor_edit': False,
}
pathlib.Path('/tmp/confluence-storage-schema.json').write_text(json.dumps(args, ensure_ascii=False))
print('Wrote /tmp/confluence-storage-schema.json')
"
# Then call MCP confluence_update_page with arguments from that JSON file.
```

### Publishing the Data Architecture decision doc (4775477367)

```bash
python3 docs/confluence-drafts/generate_funnel_data_architecture_decision_storage.py > /tmp/funnel-decision-storage.xml
python3 -c "
import json, pathlib, subprocess
xml = subprocess.check_output(
    ['python3', 'docs/confluence-drafts/generate_funnel_data_architecture_decision_storage.py'],
    text=True,
)
args = {
  'page_id': '4775477367',
  'title': 'Funnel & Journey Data Architecture Decision Document',
  'content': xml,
  'content_format': 'storage',
  'version_comment': 'Regenerate from generate_funnel_data_architecture_decision_storage.py',
  'is_minor_edit': False,
}
pathlib.Path('/tmp/confluence-4775477367.json').write_text(json.dumps(args, ensure_ascii=False))
"
# Then MCP confluence_update_page. Optionally update 4791042087 redirect (see generator docstring).
```

### Publishing the Spark page (4782587990) — job plan only, no schema DDL

```bash
python3 docs/confluence-drafts/generate_funnel_spark_implementation_storage.py > /tmp/funnel-spark-storage.xml
python3 -c "
import json, pathlib, subprocess
xml = subprocess.check_output(
    ['python3', 'docs/confluence-drafts/generate_funnel_spark_implementation_storage.py'],
    text=True,
)
args = {
  'page_id': '4782587990',
  'title': 'Funnel Spark implementation (job plan)',
  'content': xml,
  'content_format': 'storage',
  'version_comment': 'Regenerate from generate_funnel_spark_implementation_storage.py',
  'is_minor_edit': False,
}
pathlib.Path('/tmp/confluence-spark-4782587990.json').write_text(json.dumps(args, ensure_ascii=False))
print('Wrote /tmp/confluence-spark-4782587990.json')
"
# Then MCP confluence_update_page with that JSON.
```

**Repo source of truth (full detail):**

- API: `docs/architecture/funnel-server-apis.md`
- MySQL + ClickHouse alignment: `docs/architecture/funnel-mysql-clickhouse-schema.md`
- ClickHouse DDL: `backend/ingestion/clickhouse-funnel-results-schema.sql`
