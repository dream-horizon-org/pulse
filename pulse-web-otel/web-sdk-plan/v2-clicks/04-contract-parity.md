# Contract parity — Web clicks vs Android

| Aspect | Android | Web |
|--------|---------|-----|
| Signal | OTLP log | OTLP log |
| `pulse.type` | `app.click` | `app.click` |
| Body | `app.widget.click` | `app.widget.click` |
| Rage attrs | `click.is_rage`, `click.rage_count` | Same |
| Buffer + flush | Activity pause | `visibilitychange` hidden (+ SDK `pagehide` export) |
| Viewport | decorView px | `window.innerWidth/Height` (CSS px) |

**Web-only:** `rage.enabled: false` immediate path for hosts that cannot tolerate delayed singleton taps.
