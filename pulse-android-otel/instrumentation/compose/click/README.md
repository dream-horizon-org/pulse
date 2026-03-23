
# Compose Instrumentation

Status: development

## Compose version
`1.3.0` to `1.5.4`

This instrumentation has the ability to generate events when the user
performs click actions. A click is not differentiated from touch or other
input pointer events.

When an Activity becomes active, the instrumentation begins tracking
its window by registering a callback that receives events.

This instrumentation is not currently enabled by default.

## Telemetry

Data produced by this instrumentation will have an instrumentation scope
name of `io.opentelemetry.android.instrumentation.compose.click`.
This instrumentation produces the following telemetry:

### Clicks

* Type: Event
* Name: `app.screen.click`
* Description: This event is emitted when the user taps or clicks on the screen.
* See the [semantic convention definition](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/app/app-events.md#event-appscreenclick)
  for more details.

* Type: Event
* Name: `app.widget.click`
* Description: This event is emitted when the user taps on a composable that is clickable.
* See the [semantic convention definition](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/app/app-events.md#event-appwidgetclick)
  for more details.

### Attributes

Both events include:
- `app.screen.coordinate.x`, `app.screen.coordinate.y`
- `app.widget.id`, `app.widget.name` (on `app.widget.click`)
- `app.click.context` – Structured string: `label=X; type=screen|widget; source=compose` with optional `element=image|button|chip`. The `label` is present only when extractable.

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
