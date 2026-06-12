# PageHeader Component

A reusable, consistent header component for all pages in the application, based on the Critical Interactions page design.

## Features

- ✅ Consistent styling across all pages
- ✅ Optional back button with smooth animation
- ✅ Title with decorative accent bar
- ✅ Optional subtitle
- ✅ Optional count badge (with custom label)
- ✅ Optional action buttons section
- ✅ Responsive layout
- ✅ Matches project theme (teal/cyan colors)

## Usage

### Basic Example

```tsx
import { PageHeader } from "../../components";

export function MyPage() {
  return (
    <div>
      <PageHeader title="My Page Title" />
      {/* Page content */}
    </div>
  );
}
```

### With Subtitle

```tsx
<PageHeader
  title="User Engagement"
  subtitle="Monitor DAU, WAU, MAU and session depth across regions and devices"
/>
```

### With Back Button

```tsx
import { useNavigate } from "react-router-dom";

export function MyPage() {
  const navigate = useNavigate();

  return (
    <PageHeader
      title="Query Details"
      subtitle="View and analyze your query results"
      onBack={() => navigate(-1)}
    />
  );
}
```

### With Count Badge

```tsx
<PageHeader
  title="Critical Interactions"
  count={42}
  countLabel="Interactions" // Optional, defaults to "Items"
/>
```

### With Action Buttons

```tsx
<PageHeader
  title="Real-Time Querying"
  subtitle="Execute powerful SQL queries on your event data"
  onBack={() => navigate(-1)}
  actions={
    <>
      <Button variant="outline" color="teal">
        Validate Query
      </Button>
      <Button color="teal">Execute Query</Button>
    </>
  }
/>
```

### Full Example

```tsx
<PageHeader
  title="My Feature"
  subtitle="Detailed description of this feature"
  count={totalRecords}
  countLabel={totalRecords === 1 ? "Record" : "Records"}
  onBack={() => navigate(-1)}
  actions={
    <>
      <Button variant="outline">Secondary Action</Button>
      <Button color="teal">Primary Action</Button>
    </>
  }
/>
```

## Props

| Prop         | Type              | Required | Default   | Description                                         |
| ------------ | ----------------- | -------- | --------- | --------------------------------------------------- |
| `title`      | `string`          | ✅ Yes   | -         | Main page title                                     |
| `subtitle`   | `string`          | No       | -         | Optional subtitle/description                       |
| `count`      | `number`          | No       | -         | Number to display in badge                          |
| `countLabel` | `string`          | No       | `"Items"` | Label for count (e.g., "Interactions", "Records")   |
| `onBack`     | `() => void`      | No       | -         | Callback for back button (shows button if provided) |
| `actions`    | `React.ReactNode` | No       | -         | Action buttons or other controls                    |

## Styling

The component uses:

- Teal gradient accent bar on the left
- Consistent typography and spacing
- Responsive layout that stacks on mobile
- Smooth animations for interactive elements
- Theme colors: `#0ec9c2` and `#0ba09a`

## Examples in Codebase

### Real-Time Querying Page

```tsx
<PageHeader
  title="Real-Time Querying"
  subtitle="Execute powerful SQL queries on your event data in real-time"
  onBack={() => navigate(-1)}
  actions={
    <>
      <QueryHistory />
      <SuggestedQueries />
      <Button variant="outline" color="teal">
        Validate Query
      </Button>
      <Button color="teal">Execute Query</Button>
    </>
  }
/>
```

### User Engagement Page

```tsx
<PageHeader
  title="User Engagement"
  subtitle="Monitor DAU, WAU, MAU and session depth across regions, networks, devices and custom attributes"
/>
```

### Critical Interactions Page

```tsx
<PageHeader
  title="Critical Interactions"
  count={totalRecords}
  countLabel={totalRecords === 1 ? "Interaction" : "Interactions"}
/>
```

## Mobile Responsiveness

On screens smaller than 768px:

- Header content stacks vertically
- Actions section takes full width
- Back button and title maintain proper spacing
- All interactive elements remain accessible
