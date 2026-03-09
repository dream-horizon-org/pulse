---
name: pr-review
description: Workflow for reviewing pull requests using GitHub MCP tools and team coding standards. Use when reviewing PRs, checking code quality, or providing structured review feedback.
---

# PR Review Workflow

## Workflow

```
- [ ] Step 1: Fetch PR details
- [ ] Step 2: Review diff by area
- [ ] Step 3: Check cross-cutting concerns
- [ ] Step 4: Provide structured feedback
```

## Step 1: Fetch PR Details

Use GitHub MCP tools to fetch the PR:
- `get_pull_request` — title, description, author, base/head branches
- `get_pull_request_files` — list of changed files
- `get_pull_request_diff` — full diff

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
