# Compose Click Instrumentation

Status: development

## Compose version

`1.3.0` to `1.5.4`

This instrumentation has the ability to generate events when the user
performs click actions. A click is not differentiated from touch or other
input pointer events.

When an Activity becomes active, the instrumentation begins tracking
its window by registering a callback that receives events.

**Add the dependency** to enable click events. Use configuration to control context enrichment (label/element extraction), which can impact performance.

## Configuration

Add the compose-click dependency to enable click events. Use `captureContext` to control whether labels and element hints are extracted (default: true).

```kotlin
PulseSDK.INSTANCE.initialize(
    application = this,
    endpointBaseUrl = "https://your-backend.com",
    apiKey = "your-api-key",
    dataCollectionState = PulseDataCollectionConsent.ALLOWED
) {
    composeClick {
        captureContext(true)  // default; set to false to omit app.click.context entirely
    }
}
```

When `captureContext` is false, events still emit with tap coordinates and widget identity attributes, but **`app.click.context` is not set** (no label, element, or type/source string). This skips the semantics tree and node-context traversal that can improve performance.

## Flow

```
ACTION_UP (finger lift)
    │
    ▼
findTapTarget(decorView, x, y)     ← find ComposeView → findTapTargetNode (LayoutNode tree)
    │
    ▼
captureContext?                   ← if false: skip app.click.context; if true:
getContextFromSemanticsTree(...)  ← or getNodeContext (descendants/ancestors)
getElementHintForNode(node)        ← image|button|chip from Role/modifier
    │
    ▼
emit app.screen.click + app.widget.click
```

## Telemetry

Data produced by this instrumentation will have an instrumentation scope
name of `io.opentelemetry.android.instrumentation.compose.click`.
This instrumentation produces the following telemetry:

### Clicks

- Type: Event
- Name: `app.screen.click`
- Description: This event is emitted when the user taps or clicks on the screen.
- See the [semantic convention definition](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/app/app-events.md#event-appscreenclick)
  for more details.

- Type: Event
- Name: `app.widget.click`
- Description: This event is emitted when the user taps on a composable that is clickable.
- See the [semantic convention definition](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/app/app-events.md#event-appwidgetclick)
  for more details.

### Attributes

Both events include:

- `app.screen.coordinate.x`, `app.screen.coordinate.y`
- `app.widget.id`, `app.widget.name` (on `app.widget.click`)
- `app.click.context` – Structured string: `label=X; type=screen|widget; source=compose` with optional `element=image|button|chip`. The `label` is present only when extractable.

### Sample payloads

**app.screen.click** (screen-level event, emitted for every tap):

```json
{
    "name": "app.screen.click",
    "attributes": {
        "app.click.context": "label=Add to Cart; type=screen; source=compose; element=button",
        "app.screen.coordinate.x": 420,
        "app.screen.coordinate.y": 890
    }
}
```

**app.widget.click** (widget-level event, includes semantics id):

```json
{
    "name": "app.widget.click",
    "attributes": {
        "app.click.context": "label=Add to Cart; type=widget; source=compose; element=button",
        "app.widget.name": "Add to Cart",
        "app.widget.id": "12345",
        "app.screen.coordinate.x": 420,
        "app.screen.coordinate.y": 890
    }
}
```

Without a label:

```json
{
    "name": "app.screen.click",
    "attributes": {
        "app.click.context": "type=screen; source=compose",
        "app.screen.coordinate.x": 100,
        "app.screen.coordinate.y": 200
    }
}
```

## Enriching click events

Material3 `Button` and other composables often store text in child nodes, so labels may be empty without explicit semantics. Add `contentDescription` for reliable labels:

```kotlin
Button(
    onClick = { ... },
    modifier = Modifier.semantics { contentDescription = "Open Fragment activity" },
) {
    Text("Open Fragment activity")
}
```

Or use the same string for both:

```kotlin
val buttonText = "Add to Cart"
Button(
    onClick = { ... },
    modifier = Modifier.semantics { contentDescription = buttonText },
) {
    Text(buttonText)
}
```

## Installation

### Adding dependencies

```kotlin
implementation("io.opentelemetry.android.instrumentation:compose-click:0.15.0-alpha")
```
