Run backend unit tests with coverage.

1. Change to `backend/server/`
2. Run `mvn verify`
3. Parse the test results: total tests, passed, failed, skipped
4. Check JaCoCo coverage report if available
5. If any tests fail, show the failure details and suggest fixes
6. Report coverage percentages (target: 35% overall, 80% on changed files)
