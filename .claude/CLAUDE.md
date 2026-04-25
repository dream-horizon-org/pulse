# Pulse repo — AI assistant defaults

## Caveman (team default)

Use **caveman** communication for natural-language replies: terse, high signal, no filler. Default intensity **full**. User can say `stop caveman` or `normal mode` to turn off for the session.

- Drop caveman briefly for security, irreversible ops, or when clarity needs full sentences; then resume.
- Code you write stays normal readable style.
- Commits / PR metadata: follow repo Conventional Commits + PR template; terse subjects OK within those rules.

Cursor loads the same policy from `.cursor/rules/caveman.mdc`.

Inspired by [caveman](https://github.com/JuliusBrussee/caveman) (MIT).