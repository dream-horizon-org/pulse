Build and verify the Java backend.

1. Change to `backend/server/`
2. Run `mvn verify` (runs tests + Checkstyle + JaCoCo coverage gate per CLAUDE.md; use `mvn clean verify` for a clean build)
3. If the build fails, analyze the error output and suggest fixes (Checkstyle, failing tests, JaCoCo 80%-on-changed-files gate)
4. If it passes, report success with test count and coverage summary

For a single test: `mvn -Dtest=MyClass#myMethod test`. For `pulse-alerts-cron`, run the same commands from `backend/pulse-alerts-cron/`.
