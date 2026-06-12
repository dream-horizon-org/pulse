List all Cursor IDE configuration — agents, commands, skills, hooks, and rules.

1. List all **agents** (`ls .cursor/agents/`) and for each, read the first 3 lines to extract the `name` and `description` from the YAML frontmatter
2. List all **commands** (`ls .cursor/commands/`) and for each, read the first line (the command's description)
3. List all **skills** (`ls .cursor/skills/`) and for each, read the SKILL.md frontmatter to extract `name` and `description`
4. List all **hooks** (`ls .cursor/hooks/*.sh`) and for each, read the first comment line for its purpose
5. List all **rules** (`ls .cursor/rules/`) and for each, read the frontmatter to extract `description`, `globs`, and `alwaysApply`

Present the results as organized tables:

| Category | Count |
|----------|-------|
| Agents | (count) |
| Commands | (count) |
| Skills | (count) |
| Hooks | (count) |
| Rules | (count) |

Then show each category with name and one-line description.
