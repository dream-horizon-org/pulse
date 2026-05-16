# Promptfoo test suites

Add YAML suites here (e.g. per-domain files). Entry config: [`../promptfooconfig.yaml`](../promptfooconfig.yaml).

Natural-language cases for tool-selection grading live in [`../../doc/task_3/16-eval-nl-prompts.md`](../../doc/task_3/16-eval-nl-prompts.md); port those into Promptfoo when ready ([tool calling](https://promptfoo.dev/docs/configuration/tools/)).

Run from repo **`pulse-mcp/`** with Yarn:

```bash
yarn install
export GOOGLE_API_KEY="your_key"
yarn promptfoo:eval
```
