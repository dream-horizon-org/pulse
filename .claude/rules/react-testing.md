---
paths:
  - "pulse-ui/**/*.test.ts"
  - "pulse-ui/**/*.test.tsx"
  - "pulse-ui/**/*.spec.ts"
  - "pulse-ui/**/*.spec.tsx"
---

# React Testing Conventions

## Framework

Jest + React Testing Library (`@testing-library/react`, `@testing-library/jest-dom`)

## Setup

Wrap components with `MantineProvider`:

```typescript
import { render, screen } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";

function renderWithProvider(ui: React.ReactElement) {
  return render(<MantineProvider>{ui}</MantineProvider>);
}
```

## File Location

- `__tests__/` directories or `*.test.tsx` / `*.spec.tsx` co-located with components

## Best Practices

- Query by accessible roles/text, not implementation details (`getByRole`, `getByText`)
- Test user interactions with `userEvent`
- Mock API calls at the `makeRequest` level, not fetch/axios
- Use `waitFor` for async state updates
