---
paths:
  - "pulse-ui/**/*.ts"
  - "pulse-ui/**/*.tsx"
---

# React Frontend Conventions

## Project Structure

```
pulse-ui/src/
├── screens/        # Page-level screens (one folder per route)
├── components/     # Shared reusable components
├── hooks/          # Custom React hooks (API/data)
├── stores/         # Zustand stores (client state)
├── constants/      # API_ROUTES, ROUTES, OTEL semconv
├── helpers/        # Auth, cookies, makeRequest
├── types/          # Shared TypeScript types
├── mocks/          # MSW mocks
└── theme/          # Mantine theme configuration
```

## Screen / Component Pattern

```
ScreenName/
├── index.ts                 # Barrel export
├── ScreenName.tsx           # Main component
├── ScreenName.module.css    # Scoped styles
├── ScreenName.interface.ts  # Props and types (optional)
├── ScreenName.constants.ts  # Strings, options (optional)
└── components/              # Sub-components (same pattern)
```

## Custom Hook Pattern

```
useHookName/
├── index.ts
├── useHookName.ts
└── useHookName.interface.ts
```

## File Naming

| Type | Convention |
|------|------------|
| Screens/Components | PascalCase folder + `.tsx` |
| Hooks | `use*` camelCase |
| Interfaces | `*.interface.ts` |
| Constants | `*.constants.ts` |
| Styles | `*.module.css` |
| Props types | `<ComponentName>Props` |

## Stack

- **UI:** Mantine v7, Tabler icons, echarts-for-react, mantine-datatable
- **Server state:** TanStack Query v5 (`useQuery`, `useMutation`)
- **Client state:** Zustand with `devtools` middleware
- **Forms:** `react-hook-form`
- **Routing:** React Router v6 — routes in `constants/Constants.ts` as `ROUTES`

## API Integration

- Use `makeRequest<T>()` from `helpers/makeRequest/` — handles 401 refresh
- API routes in `constants/Constants.ts` → `API_ROUTES`
- Base URL: `REACT_APP_PULSE_SERVER_URL`

## Styling

- Prefer CSS modules: `import classes from "./Name.module.css"`
- Use Mantine CSS variables: `var(--mantine-spacing-md)`, `var(--mantine-color-gray-6)`
- Inline `style={{}}` acceptable for dynamic one-off styles
