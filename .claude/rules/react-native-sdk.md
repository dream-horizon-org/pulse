---
paths:
  - "pulse-react-native-otel/**/*.ts"
  - "pulse-react-native-otel/**/*.tsx"
---

# React Native SDK Conventions

## Tech Stack

TypeScript strict, React Native Builder Bob, Lefthook (pre-commit hooks)

## Project Structure

```
pulse-react-native-otel/
├── src/              # Main source
├── android/          # Android bridge
├── ios/              # iOS bridge
├── plugin/           # Expo plugin
├── example/          # Example app
└── expo-example/     # Expo example app
```

## Public API

Single `Pulse` facade exported from `index.tsx` — all public API surfaces through here.

## File Naming

- `camelCase` source files
- `*.interface.ts` for types
- `*.constants.ts` for constants
- `kebab-case` feature folders (e.g., `network-interceptor`, `navigation`)

## TypeScript Config

- `strict: true`
- `noUncheckedIndexedAccess: true`
- `verbatimModuleSyntax: true`

## Best Practices

- Use `isSupportedPlatform()` before any native calls
- ESLint flat config enforced
- Commitlint for conventional commits
- Lefthook runs lint + typecheck on pre-commit
