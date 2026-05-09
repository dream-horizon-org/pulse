# Ralph + Cursor Agent CLI

Two Ralph scripts: choose based on your CLI preference.

## Scripts

### 1. `ralph/loop.sh` (Claude CLI)
Uses Claude Code CLI (`claude --print`).

**Run:**
```bash
cd /Users/jatinkhemchandani/Desktop/pulse && \
RALPH_WORK_DIR=$PWD ./ralph/loop.sh
```

**Tools available:**
- Read, Edit, Write, Bash
- Glob, Grep
- Agent (can spawn sub-agents)
- MCP integrations

**Model:** Claude 3.5 Sonnet (Claude Code default)

---

### 2. `ralph/ralph-cursor.sh` (Cursor Agent CLI)
Uses Cursor Agent CLI (`cursor agent`).

**Run:**
```bash
cd /Users/jatinkhemchandani/Desktop/pulse && \
RALPH_WORK_DIR=$PWD ./ralph/ralph-cursor.sh
```

**Tools available:**
- Read, Edit, Write, Bash
- Glob, Grep
- ❌ No Agent tool (Cursor agents are local, no sub-agent spawning)
- ❌ No MCP integrations

**Model:** Claude Opus 4.7 (hardcoded in line 177; edit to change)

---

## Key Differences

| Feature | Claude CLI (`loop.sh`) | Cursor CLI (`ralph-cursor.sh`) |
|---------|---|---|
| **Tool: Agent** | ✅ (spawn sub-agents) | ❌ (local only) |
| **Tool: MCP** | ✅ (external services) | ❌ (not supported) |
| **Tool: WebFetch** | ✅ | ❌ |
| **Tool: WebSearch** | ✅ | ❌ |
| **Local model caching** | Backend (Anthropic) | ⚠️ Local (Cursor) |
| **Permission model** | acceptEdits / bypassPermissions | Same |
| **Per-issue Eval** | ✅ | ✅ |
| **Global Eval** | ✅ | ✅ |
| **TDD support** | ✅ | ✅ |

---

## When to Use Each

### Use Claude CLI (`loop.sh`)
- Need to spawn research agents (e.g., `/Explore` for cross-file search)
- Need external integrations (Google Drive, Slack, etc.)
- Want cloud-based model caching (faster re-runs)
- Need advanced features (WebSearch, WebFetch)

### Use Cursor CLI (`ralph-cursor.sh`)
- Want fully local execution (no cloud)
- Prefer Cursor's IDE integration during loop
- Working offline
- Want real-time IDE feedback as Ralph implements
- Testing Cursor agent capabilities

---

## Setup

### Claude CLI
```bash
# Install (if not present)
curl -fsSL https://install.claude.ai | bash

# Verify
claude --version
claude auth login  # Subscription auth
```

### Cursor CLI
```bash
# Cursor comes with `cursor` CLI command
# Verify
cursor --version
cursor agent --help
```

---

## Configuration

### Model selection (Cursor only)
Edit `ralph-cursor.sh` line 177:
```bash
(cd "$WORK_DIR" && cursor agent --model claude-opus-4-7 2>&1) |
```

Available models: `claude-opus-4-7`, `claude-sonnet-4-6`, `claude-haiku-4-5`

### Permission mode
Both scripts support `--bypass` flag:
```bash
./ralph/loop.sh --bypass                    # Claude CLI
./ralph/ralph-cursor.sh --bypass            # Cursor CLI
```

### Max iterations
```bash
./ralph/loop.sh --max-iters 50
./ralph/ralph-cursor.sh --max-iters 50
```

---

## Exit codes (both scripts)

- **0:** COMPLETE or NO MORE TASKS (clean stop)
- **1:** EVAL_FAILED (per-issue gate failed)
- **2:** GLOBAL EVAL failed (regression detected)
- **3:** Hit max iterations with work remaining
- **64+:** Pre-flight / config errors

---

## Logs

- **Claude CLI:** `ralph/.logs/iter-*.log`
- **Cursor CLI:** `ralph/.logs-cursor/iter-*.log`

Progress tracked in:
- **Claude CLI:** `progress.txt`
- **Cursor CLI:** `progress-cursor.txt`

---

## Example runs

**TDD screen navigation signals (Claude CLI):**
```bash
cd /Users/jatinkhemchandani/Desktop/pulse && \
RALPH_WORK_DIR=$PWD ./ralph/loop.sh
```

**Same, but with Cursor Agent:**
```bash
cd /Users/jatinkhemchandani/Desktop/pulse && \
RALPH_WORK_DIR=$PWD ./ralph/ralph-cursor.sh
```

**Cursor, offline mode, 20 iters:**
```bash
cd /Users/jatinkhemchandani/Desktop/pulse && \
RALPH_WORK_DIR=$PWD ./ralph/ralph-cursor.sh --max-iters 20
```

---

## Troubleshooting

### "claude CLI not on PATH"
Install Claude Code: https://claude.ai/code

### "cursor CLI not on PATH"
Install Cursor editor, which includes `cursor` command. Or add Cursor to PATH:
```bash
export PATH="/Applications/Cursor.app/Contents/MacOS:$PATH"
```

### Eval block fails
- Check issue `.md` file has `## Eval` with fenced bash block
- Test Eval manually: `bash <(awk '...' issues/NN-*.md)`

### Permission denied on listener removal (Cursor)
Use `--bypass` flag when running `ralph-cursor.sh`:
```bash
./ralph/ralph-cursor.sh --bypass
```

---

## Contributing

To support a third CLI (e.g., Anthropic SDK, other frameworks):
1. Copy `ralph/loop.sh` or `ralph/ralph-cursor.sh`
2. Replace CLI invocation (line ~177)
3. Adapt tool names if needed
4. Test with a small issue (e.g., issue 01)
5. Update this doc

