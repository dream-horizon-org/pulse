Create a pull request for the current branch.

1. Run `git status` to check current branch and changes
2. Run `git log main..HEAD --oneline` to see all commits on this branch
3. Run `git diff main...HEAD` to understand the full scope of changes
4. Draft a PR following the team template:
   - **Summary**: 1-2 line description
   - **Context / Motivation**: why this change, link issues
   - **What Changed**: bullets grouped by Backend, UI, Android SDK, React Native SDK, Deploy / Infra
   - **Screenshots / Recordings**: N/A unless UI changes
5. Push the branch if not already pushed: `git push -u origin HEAD`
6. Create the PR using `gh pr create` with the drafted content
7. Return the PR URL
