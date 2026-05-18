# Promptfoo test suites

Add YAML suites here (e.g. per-domain files). Entry config: [`../promptfooconfig.yaml`](../promptfooconfig.yaml).

**Tool definitions:** run `yarn generate:promptfoo-tools` (or `yarn promptfoo:eval`, which runs build + codegen first). That script uses the MCP client protocol against **`node dist/index.js`** and writes **`../tools.generated.yaml`** (gitignored). Do not mirror tools by hand.

Natural-language cases for tool-selection grading are listed in [`../../doc/task_3/16-eval-nl-prompts.md`](../../doc/task_3/16-eval-nl-prompts.md).

Run from repo **`pulse-mcp/`** with Yarn:

```bash
yarn install
export GOOGLE_API_KEY="your_key"
export PULSE_BASE_URL="http://localhost:8080"
export PULSE_API_KEY="pulse_mcp_…"
yarn promptfoo:eval
```
