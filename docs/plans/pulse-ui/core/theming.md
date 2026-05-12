# Theming

Mantine v7 + CSS Modules. No Tailwind, no styled-components.

## Mantine setup

- Theme is defined in `pulse-ui/src/theme/Theme.ts` and exported from
  `src/theme/index.ts`.
- `MantineProvider` wraps the app in `src/index.tsx` with the theme and
  `defaultColorScheme`.
- Global Mantine CSS is imported at module scope in `src/index.tsx` /
  `index.css`.

## CSS Modules

Convention per component folder:

```
ComponentName/
  index.ts                # barrel re-export
  ComponentName.tsx       # component
  ComponentName.module.css
  ComponentName.interface.ts   # (optional) props + types
  ComponentName.constants.ts   # (optional) local constants
```

Inside `*.module.css` always use Mantine CSS variables instead of
hard-coded values:

```css
.root {
  padding: var(--mantine-spacing-md);
  color: var(--mantine-color-text);
  background: var(--mantine-color-body);
  border-radius: var(--mantine-radius-md);
}
```

Spacing scale: `xs`, `sm`, `md`, `lg`, `xl`. Use the same scale for
gap/padding/margin.

## When to use Mantine primitives vs CSS Modules

- Layout (`Stack`, `Group`, `Flex`, `Grid`, `Box`) - Mantine.
- Inputs (`TextInput`, `Select`, `MultiSelect`, `Textarea`,
  `DatePickerInput`) - Mantine.
- Buttons, badges, tooltips, popovers, menus - Mantine.
- Custom visualizations, dashboard cards, bespoke layout - CSS Modules
  on top of Mantine primitives.

## Charts

`src/components/Charts/` wraps the charting layer (ECharts /
`@mantine/charts` depending on the chart). Always wrap chart usage with
`GraphSkeleton` while loading and `ErrorAndEmptyState` on error or empty.

## Dark mode

Mantine handles dark mode through CSS variables. Components should not
read `useMantineColorScheme()` unless they need to swap an asset (e.g.
SVG logo). All custom CSS must rely on `var(--mantine-color-*)` so dark
mode "just works".

## Lint rules

- No inline `style={{ ... }}` for layout (use Mantine props or CSS
  Modules).
- No hard-coded colors / spacings outside `Theme.ts`.
- One component per file; one CSS module per component.

## Rebuild recipe

1. Install `@mantine/core`, `@mantine/hooks`, `@mantine/dates`,
   `@mantine/notifications`, `@mantine/charts` as needed.
2. Add `src/theme/Theme.ts` with brand colors, fonts and radii.
3. Mount `MantineProvider` in `index.tsx`; add
   `<Notifications/>` and `<ModalsProvider/>` if used.
4. For each new component, create the four-file folder and use Mantine
   variables in the CSS module.
