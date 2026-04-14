---
name: run-backend-tests
description: Run backend tests with coverage report. Use when asked to run tests or check test coverage.
allowed-tools: Bash(mvn *)
---

Run:

```bash
cd backend/server && mvn verify
```

Report:
- Total tests: passed / failed / skipped
- JaCoCo coverage: overall % and changed-files %
- Any test failures with the test name and failure message
- Checkstyle violations if any

Thresholds: 35% overall, 80% on changed files. Flag if either is missed.
