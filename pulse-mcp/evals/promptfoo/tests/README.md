# Promptfoo test suites

Add YAML suites here (e.g. per-domain files). Entry config: [`../promptfooconfig.yaml`](../promptfooconfig.yaml).

**Tool definitions:** the ADK provider ([`../providers/gemini-adk-agent.mjs`](../providers/gemini-adk-agent.mjs)) connects to **`node dist/index.js`** over MCP stdio and discovers tools from `tools/list` at eval time. Run `yarn build` (or `yarn promptfoo:eval`, which builds first). Do not mirror tools by hand.

Natural-language cases for tool-selection grading live in the YAML suites in this directory.

Run from repo **`pulse-mcp/`** with Yarn:

```bash
yarn install
export GOOGLE_API_KEY="your_key"
export PULSE_BASE_URL="http://localhost:8080"
export PULSE_API_KEY="pulse_mcp_…"
yarn promptfoo:eval
```
