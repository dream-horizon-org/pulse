# UIKit Tap Auto-Instrumentation

Automatically intercepts user taps in the **UIKit** layer and emits an `app.widget.click` log event with rich context — no per-view instrumentation required.

This module is **UIKit-scoped** on purpose: it observes `UIWindow.sendEvent` and walks the `UIView` hierarchy. **Reliable SwiftUI auto-instrumentation is not available at this layer** — there is no supported public API that maps touches to SwiftUI view identity and “is this tappable?” the way we do for `UIControl` and cells. We therefore **do not** ship SwiftUI-specific heuristics here; that is an engineering boundary, not a missing polish item. See [SwiftUI and mixed apps](#swiftui-and-mixed-apps) below.

## How it works

`UIWindowSwizzler` swizzles `UIWindow.sendEvent(_:)` at the class level on first SDK initialization. Every touch event passes through this single interception point:

1. **Touch start (`.began`)** — records the start position.
2. **Touch end (`.ended`)** — checks the slop distance. If the finger moved more than 10 pt from the start it is treated as a scroll and no event is emitted (mirrors Android's `touchSlop` mechanism).
3. **Hit test** — calls `UIWindow.hitTest(_:with:)` to find the deepest view under the finger, then walks **up** the superview chain to the nearest view that matches [tap target rules](#tap-target-detection).
4. **Original dispatch** — the original `sendEvent` is called so touch handling is unaffected.
5. **Event emission** — `app.widget.click` is emitted after dispatch, so `UISegmentedControl.selectedSegmentIndex` and similar state already reflect the tap.

## Tap target detection

After `hitTest`, the SDK walks **up the superview chain** (hit view → `superview` → …) and picks the **first** view that satisfies **any** of the rules below. Rules are evaluated in this order for each candidate:

1. Reject **`UIScrollView`** (including `UITableView` / `UICollectionView`) as the resolved target.
2. Accept **`UIControl`** (`UIButton`, `UISwitch`, `UISegmentedControl`, `UIStepper`, `UISlider`, **`UITextField`**, …).
3. Accept **`UITableViewCell`** / **`UICollectionViewCell`**.
4. Accept if the view’s **`gestureRecognizers`** include tap, long-press, or swipe (**not** pan).
5. Accept **`.button`** or **`.link`** accessibility traits.

| Condition | Examples / notes |
|---|---|
| **Not** a `UIScrollView` | Scroll views never become the emitted widget; empty table/scroll chrome does not collapse to `UITableView`. Taps still resolve to **cells**, **controls**, or a subview whose **ancestor** (walking up) owns a discrete gesture. |
| `UIControl` | Standard controls; text fields count as controls (labels are PII-safe — see below). |
| `UITableViewCell` / `UICollectionViewCell` | Selection is often delivered via the scroll view; the cell is still the meaningful target. |
| Discrete gesture **on that view** | Only `gestureRecognizers` attached to the **candidate** are inspected. A tap recognizer on a **parent** is found when the walk reaches that parent. **`UIPanGestureRecognizer`** is ignored so scrolling and drag surfaces are not logged as taps. |
| Accessibility `.button` / `.link` | Custom tappable views that are not `UIControl`. |

### Limitations (by design)

- **Pan-only interactions** (`UIPanGestureRecognizer` without tap/long-press/swipe) are not treated as taps.
- **Superview chain only** — a gesture on a **sibling** view is not visible when walking from the hit view; rare layouts can miss a tap target.
- **`UIContextMenuInteraction`** (peek/pop menu) without an accompanying tap/long-press/swipe recognizer on the same path may not produce an `app.widget.click`.
- **`.adjustable`** and other traits alone do not qualify unless the view is already a `UIControl` or matches another rule (most sliders/steppers are `UIControl`).

## SwiftUI and mixed apps

### Why SwiftUI-specific auto-instrumentation is not included

**Auto-instrumentation** in this module means: one swizzle on `UIWindow.sendEvent`, then `hitTest` + rules on `UIView`. That model works for UIKit because controls and cells are real `UIView` types with predictable behavior.

**SwiftUI does not expose an equivalent at the UIKit boundary.** The framework renders into private views (hosting views, drawing surfaces, internal wrappers). From outside SwiftUI you only see that **UIKit** tree — not the `Button` vs `Text` vs `onTapGesture` structure, and not a stable, documented contract that says “this touch activated control X.” Anything we could add would be **guesswork** on private implementation details (and would break or mis-fire across OS versions).

So we **intentionally do not** add a “SwiftUI mode” to this swizzler right now: it would imply a level of accuracy we cannot honestly guarantee. **Mixed UIKit + SwiftUI apps** still get **best-effort** coverage where SwiftUI surfaces look like normal UIKit (e.g. accessibility traits, some system controls); for **first-class SwiftUI analytics**, use **explicit instrumentation** (your own events) or a **SwiftUI-native** approach, not this UIKit-only auto path.

### What you actually see under a SwiftUI screen

SwiftUI builds a tree of **private** UIKit views. A typical hit might look like this (class names are illustrative; Apple may rename internals between OS versions):

```
CGDrawingView (or similar leaf)
  → …
  → PlatformViewRepresentableAdaptor / _UIGraphicsView / other wrappers
  → _UIHostingView<…>
  → …
```

From this alone you **cannot** reliably know:

- Whether the user tapped something with an `onTapGesture` vs decorative `Text`.
- Whether a control is disabled, covered, or hit-tested away.
- What the “logical” SwiftUI element is (that information lives in SwiftUI’s layer, not in a stable public UIKit API).

**Heuristic tradeoffs**

- If you **treat `_UIHostingView` as a click**: you get **false positives** (any tap anywhere in that hosting subtree collapses to one widget name).
- If you **require accessibility traits** (e.g. `.button` / `.link`) to count a path as interactive: you get **false negatives** for tappable views that do not set those traits, and **false positives** for non-tappable elements that still expose button-like accessibility for VoiceOver.

Because of that, this SDK applies **only the same UIKit rules** as everywhere else — no separate SwiftUI branch. That keeps behavior predictable and honest about what we can infer from `UIView` alone.

## Label extraction

When `captureContext` is enabled, the SDK extracts a human-readable label from the **resolved** tap target using the following priority:

```
UISegmentedControl.titleForSegment(at: selectedSegmentIndex)   ← selected segment only
  ↓ (not a segmented control)
UILabel.text (if view is a UILabel)
  ↓
Direct UILabel subview .text  (e.g. UIButton.titleLabel)
  ↓
.accessibilityLabel
  ↓
Recursive UILabel scan: max depth 4, max 5 segments, " | " joined, ≤ 200 chars
```

### PII safety

Text input controls (`UITextField`, `UITextView`, `UISearchBar`) never have their typed text captured.

## Emitted event

### Event name: `app.widget.click`

| Attribute | Type | Example | Notes |
|---|---|---|---|
| `click.type` | String | `"good"` / `"dead"` | Good when a target is resolved, dead when no interactive target is found |
| `click.is_rage` | Bool | `true` | Present for rage events |
| `click.rageCount` | Int | `5` | Present for rage events, total taps in the rage cluster |
| `app.widget.name` | String | `"UIButton"` | Runtime class name — consistent type identifier |
| `app.widget.id` | String | `"checkout_button"` | From `accessibilityIdentifier` when set |
| `app.click.context` | String | `"label=Add to Cart"` | Present only when a label is found and `captureContext` is true |
| `app.screen.coordinate.x` | Int | `142` | Touch X in window coordinates |
| `app.screen.coordinate.y` | Int | `380` | Touch Y in window coordinates |
| `device.screen.width` | Int | `375` | Viewport width in points |
| `device.screen.height` | Int | `812` | Viewport height in points |
| `app.screen.coordinate.nx` | Double | `0.378` | Normalized X (`x / width`) |
| `app.screen.coordinate.ny` | Double | `0.468` | Normalized Y (`y / height`) |

Additional attributes are automatically enriched by the processor chain: `screen.name`, `session.id`, network status, aspect ratio, etc.

Notes:
- Dead clicks intentionally omit widget fields because no target view was resolved.
- Rage clicks use the same `app.widget.click` event and include `click.is_rage = true`.

## Coverage (UIKit)

| Scenario | Captured | Label source |
|---|---|---|
| `UIButton` with title | ✅ | `titleLabel.text` |
| `UIButton` icon-only | ✅ | `accessibilityLabel` if set, else class name |
| `UISegmentedControl` | ✅ | Selected segment title only |
| `UISwitch`, `UIStepper`, `UISlider` | ✅ | `accessibilityLabel` if set |
| `UIView` + `UITapGestureRecognizer` | ✅ | Recursive label scan |
| `UIView` + `UILongPressGestureRecognizer` / `UISwipeGestureRecognizer` | ✅ | Same as tap (discrete gesture) |
| `UITableViewCell` | ✅ | Recursive label scan of cell subviews |
| `UICollectionViewCell` | ✅ | Recursive label scan of cell subviews |
| `UITextField` tap | ✅ | `accessibilityLabel` only — typed text never captured |
| Blank `UIScrollView` / table background | ❌ (by design) | — |
| SwiftUI-only screens (`UIHostingController`, etc.) | ⚠️ **Best effort** | Same UIKit rules on whatever `UIView` tree SwiftUI builds; **not** full SwiftUI auto-instrumentation (see above). |

## Configuration

```swift
Pulse.shared.initialize(
    endpointBaseUrl: "...",
    projectId: "...",
    instrumentations: { config in
        config.uiKitTap { tap in
            tap.enabled(true)          // default: false
            tap.captureContext(true)   // default: false
            tap.rage { rage in         // defaults: 2000 ms / 3 taps / 50 pt radius
                rage.timeWindowMs = 2000
                rage.rageThreshold = 3
                rage.radiusPt = 50
            }
        }
    }
)
```

### `captureContext: false`

Skips all view traversal for context extraction (no recursive label scan, no `UILabel` inspection). Click classification still works (`good`/`dead`/`rage`) and coordinates are still emitted. Useful for apps with very large or deeply nested view hierarchies where label scanning has measurable overhead.
