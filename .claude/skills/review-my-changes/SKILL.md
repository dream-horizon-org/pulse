---
name: review-my-changes
description: Review uncommitted changes against Pulse coding conventions. Use before creating a PR.
context: fork
agent: pr-reviewer
---

Review the current uncommitted changes:

**Changed files:**
!`git diff --name-only HEAD`

**Full diff:**
!`git diff HEAD`

**Recent commits:**
!`git log --oneline -5`

Review these changes against Pulse conventions:
1. Identify which areas are touched (backend / UI / SDK / deploy)
2. Apply the relevant checklist for each area
3. Flag any Critical issues that must be fixed before merging
4. List Suggestions and Nits separately
5. Confirm commit message format follows `<type>(<scope>): <description>`
