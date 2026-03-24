# View Click Instrumentation

Status: development

This instrumentation has the ability to generate events when the user
performs click actions. A click is not differentiated from touch or other
input pointer events.

When an Activity becomes active, the instrumentation begins tracking
its window by registering a callback that receives events.

**Add the dependency** to enable click events. Use configuration to control context enrichment (label/element extraction), which can impact performance.

## Configuration

Add the view-click dependency to enable click events. Use `captureContext` to control whether labels and element hints are extracted (default: true).

```kotlin
PulseSDK.INSTANCE.initialize(
    application = this,
    endpointBaseUrl = "https://your-backend.com",
    apiKey = "your-api-key",
    dataCollectionState = PulseDataCollectionConsent.ALLOWED
) {
    viewClick {
        captureContext(true)  // default; set to false to omit app.click.context entirely
    }
}
```

When `captureContext` is false, events still emit with tap coordinates and widget identity attributes (`app.screen.coordinate.*`, `app.widget.*`), but **`app.click.context` is not set** (no label or element string).

## Flow

```
ACTION_UP (finger lift)
    │
    ▼
findTargetForTap(decorView, x, y)  ← hit-test culled BFS (only path to tap)
    │
    ▼
captureContext?                   ← if false: skip app.click.context; if true:
getViewContextLabel(view)          ← TextView/EditText/ViewGroup label extraction
getElementHint(view)               ← image|button|chip
    │
    ▼
emit app.widget.click
```

## Telemetry

Data produced by this instrumentation will have an instrumentation scope
name of `io.opentelemetry.android.instrumentation.view.click`.
This instrumentation produces the following telemetry:

### Clicks

- Type: Event
- Name: `app.widget.click`
- Description: Emitted when the user taps a clickable view. Tap coordinates (`app.screen.coordinate.*`) reflect the tap position. Jetpack Compose views are not handled by this module (use compose-click).
- See the [semantic convention definition](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/app/app-events.md#event-appwidgetclick)
  for more details.

### Attributes

- `app.screen.coordinate.x`, `app.screen.coordinate.y` — tap position (window coordinates)
- `app.widget.id`, `app.widget.name`
- `app.click.context` — Optional. When `captureContext` is true: `label=X` and/or `element=image|button|chip`, semicolon-separated (e.g. `label=Add to Cart; element=button`). Omitted when nothing extractable.

### Sample payload

```json
{
    "name": "app.widget.click",
    "attributes": {
        "app.click.context": "label=Add to Cart; element=button",
        "app.widget.name": "add_btn",
        "app.widget.id": "2131234567",
        "app.screen.coordinate.x": 420,
        "app.screen.coordinate.y": 890
    }
}
```

Without a label (e.g. clickable view with no extractable text), `app.click.context` may be omitted or contain only `element=…` when the view type is inferred.

## Enriching click events

To get readable labels in `app.click.context`, add semantics to your views:

1. **TextView** — Label is read from `text` automatically.
2. **Buttons / other views** — Set `android:contentDescription` in XML or `view.contentDescription = "..."` in code.
3. **Clickable ViewGroup (Card, FrameLayout, etc.)** — Label merges up to 5 segments from contentDescription and descendant TextViews. Truncation happens at segment boundaries (drops segments from end) to avoid cutting mid-word. Max length: 200 chars.
4. **Clickable ImageView/ImageButton** — Uses contentDescription when present. When absent, no label but `element=image` may be set so analytics know it was an image tap.
5. **Buttons / Chips** — `element=button` or `element=chip` is added when the view type can be inferred (Button, MaterialButton, Material Chip), to help analytics distinguish tap targets.

Example (XML):

```xml
<Button
    android:id="@+id/add_btn"
    android:contentDescription="Add to Cart"
    ... />
```

Example (code):

```kotlin
view.contentDescription = "Submit"
```

Without `contentDescription`, `app.widget.name` will use the resource ID (e.g. `add_btn`) when available.

## Installation

### Adding dependencies

```kotlin
implementation("io.opentelemetry.android.instrumentation:view-click:0.15.0-alpha")
```
