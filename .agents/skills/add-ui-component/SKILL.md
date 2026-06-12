---
name: add-ui-component
description: Step-by-step workflow for adding a new reusable component to pulse-ui. Use when creating a shared component in pulse-ui/src/components/ (not a full screen/page).
---

# Add UI Component

## Workflow

```
- [ ] Step 1: Create component folder structure
- [ ] Step 2: Define types/interfaces
- [ ] Step 3: Build the component
- [ ] Step 4: Add styles
- [ ] Step 5: Create barrel export
- [ ] Step 6: Verify build
```

## Step 1: Create Folder

```
pulse-ui/src/components/MyComponent/
├── MyComponent.tsx
├── MyComponent.interface.ts
├── MyComponent.module.css
├── index.ts
└── components/           (if sub-components needed)
```

## Step 2: Define Types

`MyComponent.interface.ts`:
```typescript
export interface MyComponentProps {
  title: string;
  variant?: "default" | "compact";
  onAction?: () => void;
}
```

## Step 3: Build the Component

Use Mantine components, accept props via interface:
```typescript
import { Box, Text } from "@mantine/core";
import { MyComponentProps } from "./MyComponent.interface";
import classes from "./MyComponent.module.css";

export function MyComponent({ title, variant = "default", onAction }: MyComponentProps) {
  return (
    <Box className={classes.container}>
      <Text>{title}</Text>
    </Box>
  );
}
```

## Step 4: Styles

`MyComponent.module.css` using Mantine CSS variables:
```css
.container {
  padding: var(--mantine-spacing-md);
  border-radius: var(--mantine-radius-md);
}
```

## Step 5: Barrel Export

`index.ts`:
```typescript
export { MyComponent } from "./MyComponent";
export type { MyComponentProps } from "./MyComponent.interface";
```

## Step 6: Verify

```bash
cd pulse-ui && yarn build
```
