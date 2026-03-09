Review all uncommitted changes in the current branch.

1. Run `git status` to see all modified, added, and untracked files
2. Run `git diff` to see the full diff of changes
3. Group changes by area: Backend, Frontend, AI Agent, Deploy, SDK, Docs
4. For each area, review against the project conventions:
   - Backend: Service/DAO/DTO patterns, error handling, MapStruct, tests
   - Frontend: Screen folder convention, Mantine usage, TanStack Query, CSS modules
   - AI Agent: FunctionTool pattern, STATE_KEYS, registries
   - Deploy: Health checks, env vars in .env.example
5. Check for security issues: hardcoded secrets, exposed credentials
6. Provide structured feedback: Critical issues, Suggestions, Nice-to-have
7. Suggest a commit message following Conventional Commits format
