---
name: build-backend
description: Build and test the Java backend. Use when asked to build, compile, or run backend tests.
allowed-tools: Bash(mvn *)
---

Run the following from the repo root:

```bash
cd backend/server && mvn clean install
```

Report:
- Build SUCCESS or FAILURE
- Number of tests passed/failed
- Any Checkstyle violations
- JaCoCo coverage if shown

If it fails, show the first ERROR or FAILURE section from the output.
