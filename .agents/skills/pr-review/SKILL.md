---
name: pr-review
description: Workflow for reviewing pull requests via GitHub MCP or GitHub CLI (gh) and team coding standards. Use when reviewing PRs, checking code quality, or providing structured review feedback.
---

# PR Review Workflow

## Workflow

```
- [ ] Step 1: Fetch PR details (MCP or gh — see below)
- [ ] Step 2: Review diff by area
- [ ] Step 3: Check cross-cutting concerns
- [ ] Step 4: Provide structured feedback
```

## Step 1: Fetch PR Details

Use **one** of the following. Prefer MCP when it is connected and healthy; otherwise use **GitHub CLI** in the project repo (or any cwd) with a logged-in user.

### Option A: GitHub MCP (when available)

Read the MCP tool schemas in the Cursor `mcps` folder, then call the documented tools. Typical names:

- `get_pull_request` — title, description, author, base/head branches
- `get_pull_request_files` — list of changed files
- `get_pull_request_diff` — full diff

### Option B: GitHub CLI (`gh`)

Requires [GitHub CLI](https://cli.github.com/) installed and authenticated (`gh auth status` succeeds).

**One-time login (interactive):**

```bash
gh auth login
```

Follow the prompts: GitHub.com vs Enterprise, HTTPS vs SSH, then **either** “Login with a web browser” **or** “Paste an authentication token.”

**Credentials you need:**

| Method | What you use |
|--------|----------------|
| **Web browser** | Your normal GitHub account sign-in (SSO/org access if the org requires it). No manual token copy if you use the browser flow. |
| **Personal Access Token (HTTPS)** | A **classic** PAT with at least `repo` (private repos), or a **fine-grained** token with **Repository access** to the target repo and permissions: **Contents** Read, **Metadata** Read, **Pull requests** Read. |

**Verify:**

```bash
gh auth status
```

**Fetch PR metadata and changed files (replace `OWNER`, `REPO`, `N`):**

```bash
gh pr view N --repo OWNER/REPO --json title,body,author,baseRefName,headRefName,state,commits,files
```

**Full diff (patch):**

```bash
gh pr diff N --repo OWNER/REPO
```

**List file names only:**

```bash
gh pr view N --repo OWNER/REPO --json files --jq '.files[].path'
```

**Checkout the PR branch locally (optional, for `git` inspection):**

```bash
gh pr checkout N --repo OWNER/REPO
```

If `gh` prints `401` or `403`, complete `gh auth login` or refresh SSO (`gh auth refresh -h github.com` with `-s` scopes if needed).

After fetching, proceed to Step 2 using the same review checklists whether data came from MCP or `gh`.

## Step 2: Review by Area

### Backend Changes (`backend/`)
- [ ] Service interface + impl pattern
- [ ] ServiceError used for errors (not raw exceptions)
- [ ] SQL in Queries class (not inline)
- [ ] RxJava types (Single, Maybe, Completable)
- [ ] MapStruct mapper (not manual mapping)
- [ ] Lombok annotations on DTOs
- [ ] Tests with >80% coverage on changed code
- [ ] Checkstyle: 140 char lines, 2-space indent

### Frontend Changes (`pulse-ui/`)
- [ ] Screen folder convention followed
- [ ] Mantine components (not raw HTML)
- [ ] TanStack Query for server state
- [ ] makeRequest for API calls
- [ ] Types in .interface.ts files
- [ ] CSS modules (not inline styles)

### AI Agent Changes (`pulse_ai/`)
- [ ] FunctionTool pattern with ToolContext
- [ ] STATE_KEYS for state (not strings)
- [ ] Registry entries (not hardcoded)
- [ ] SQL safety enforced

### Deploy Changes (`deploy/`)
- [ ] Health check included
- [ ] Dependencies with service_healthy condition
- [ ] .env.example updated for new vars
- [ ] No secrets in committed files

## Step 3: Cross-Cutting

- Alert metric changes → all 5 layers updated?
- New env vars → added to `.env.example`?
- New API endpoints → DTO + service + DAO + mapper + tests?
- Security: no secrets, keys, or credentials in code?

## Step 4: Feedback Format

```markdown
## Review Summary

### Critical (must fix)
- ...

### Suggestions (should improve)
- ...

### Nice to Have (optional)
- ...
```

Include specific file:line references and code examples for fixes.
