Run frontend unit tests with coverage.

1. Change to `pulse-ui/`
2. Run `yarn install` if `node_modules` doesn't exist
3. Run `yarn test --watchAll=false --coverage`
4. Parse the test results: total suites, passed, failed, skipped
5. If any tests fail, show the failure details and suggest fixes
6. Report coverage percentages if available
