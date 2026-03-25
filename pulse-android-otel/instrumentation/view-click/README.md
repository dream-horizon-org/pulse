# View Click Instrumentation

Status: development

This instrumentation has the ability to generate events when the user
performs click actions. A click is not differentiated from touch or other
input pointer events.

When an Activity becomes active, the instrumentation begins tracking
its window by registering a callback that receives events.

**Add the dependency** to enable click events. Use configuration to control context enrichment.

## Configuration

Add the view-click dependency to enable click events.

```kotlin
implementation("io.opentelemetry.android.instrumentation:view-click:0.15.0-alpha")
```

Use `captureContext` to control whether labels are extracted (default: true).


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

When `captureContext` is false, events still emit with tap coordinates and widget identity attributes (`app.screen.coordinate.*`, `app.widget.*`), but **`app.click.context` is not set**.

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
- `app.click.context` — Optional. When `captureContext` is true: structured **`key=value`** segments separated by `; `.

### Sample payload

```json
{
    "name": "app.widget.click",
    "attributes": {
        "app.click.context": "label=Add to Cart",
        "app.widget.name": "add_btn",
        "app.widget.id": "2131234567",
        "app.screen.coordinate.x": 420,
        "app.screen.coordinate.y": 890
    }
}
```

Without a label (e.g. clickable view with no extractable text), `app.click.context` is omitted.

## Enriching click events

To get readable labels in `app.click.context`, add semantics to your views:

1. **TextView** — Label is read from `text` automatically.
2. **Buttons / other views** — Set `android:contentDescription` in XML or `view.contentDescription = "..."` in code.
3. **Clickable ViewGroup (Card, FrameLayout, etc.)** — Label merges up to 5 segments from contentDescription and descendant TextViews. Truncation happens at segment boundaries (drops segments from end) to avoid cutting mid-word. Max length: 200 chars.
4. **Clickable ImageView/ImageButton** — Uses contentDescription when present. When absent, rely on `app.widget.name` (resource name or id).

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
