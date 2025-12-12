# Interaction Instrumentation

**Track and measure user flows across multiple events in your Android application.**

Monitor complex user journeys (interactions) based on event sequences. For example, track a "Checkout Flow" from cart → payment → confirmation, or a "Login Journey" from splash → login → home screen.

---

## Overview

Interactions are **server-configured event sequences** that the SDK tracks automatically. When users complete a sequence, the SDK creates a span with timing and event data.

### Key Features

- 🎯 **Server-Configured**: Define interactions via API, no app updates needed
- 📊 **Automatic Tracking**: SDK matches events to configured interactions
- ⏱️ **Performance Metrics**: Capture timing for entire user flows
- 🔍 **Event Timeline**: See all events in the interaction

---

## Quick Start

### 1. Enable in SDK Initialization

```kotlin
PulseSDK.INSTANCE.initialize(
    application = this,
    endpointBaseUrl = "https://your-backend.com"
) {
    interaction {
        enabled(true)
        // if not set default to below URL
        setConfigUrl { "http://10.0.2.2:8080/interactions" }
    }
}
```

**Network Configuration:**
- Emulator: `http://10.0.2.2:8080/` (default)
- Production: `https://api.yourservice.com/`

### 2. Track Events

```kotlin
PulseSDK.INSTANCE.trackEvent(
    name = "cart_viewed",
    observedTimeStampInMs = System.currentTimeMillis(),
    params = mapOf("itemCount" to 3)
)
```

The Interaction instrumentation automatically:
1. Listens to tracked events
2. Matches them against API-configured sequences
3. Creates spans when sequences complete

---

## API Configuration

The SDK fetches configurations from:
```
GET {baseUrl}/v1/interactions/all-active-interactions
```

### Response Format

```json
{
  "data": [
    {
      "id": 1,
      "name": "CheckoutFlow",
      "events": [
        { "name": "cart_viewed", "props": [], "isBlacklisted": false },
        { "name": "payment_initiated", "props": [], "isBlacklisted": false },
        { "name": "order_confirmed", "props": [], "isBlacklisted": false }
      ],
      "globalBlacklistedEvents": [],
      "uptimeLowerLimitInMs": 5000,
      "uptimeMidLimitInMs": 15000,
      "uptimeUpperLimitInMs": 30000,
      "thresholdInMs": 300000
    }
  ],
  "error": null
}
```

### Configuration Fields

| Field | Type | Description |
|-------|------|-------------|
| `name` | String | Interaction name (used as span name) |
| `events` | Array | Ordered sequence of events to match |
| `globalBlacklistedEvents` | Array | Events to ignore during matching |
| `uptimeLowerLimitInMs` | Long | Fast interaction threshold |
| `uptimeMidLimitInMs` | Long | Normal interaction threshold |
| `uptimeUpperLimitInMs` | Long | Slow interaction threshold |
| `thresholdInMs` | Long | Max time between events (timeout) |

---

## Example: Checkout Flow

### Backend Configuration

```json
{
  "name": "CheckoutFlow",
  "events": [
    { "name": "cart_viewed", "isBlacklisted": false },
    { "name": "payment_entered", "isBlacklisted": false },
    { "name": "order_placed", "isBlacklisted": false }
  ],
  "uptimeLowerLimitInMs": 5000,
  "uptimeMidLimitInMs": 15000,
  "uptimeUpperLimitInMs": 30000,
  "thresholdInMs": 300000
}
```

### App Code

```kotlin
class CheckoutActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PulseSDK.INSTANCE.trackEvent("cart_viewed", System.currentTimeMillis())
    }
    
    fun onPaymentSubmit() {
        PulseSDK.INSTANCE.trackEvent(
            name = "payment_entered",
            observedTimeStampInMs = System.currentTimeMillis(),
            params = mapOf("paymentMethod" to "credit_card")
        )
    }
    
    fun onOrderSuccess(orderId: String) {
        PulseSDK.INSTANCE.trackEvent(
            name = "order_placed",
            observedTimeStampInMs = System.currentTimeMillis(),
            params = mapOf("orderId" to orderId)
        )
    }
}
```

### Generated Span

```
Span: CheckoutFlow (12.5s)
├─ cart_viewed (t=0ms)
├─ payment_entered (t=8.9s) {paymentMethod: credit_card}
└─ order_placed (t=12.5s) {orderId: ORD-12345}

Category: normal (5s < 12.5s < 15s)
```

---

## Advanced Features

### Event Property Matching

Match events based on property values:

```json
{
  "name": "screen_viewed",
  "props": [
    { "name": "screen_name", "value": "checkout", "operator": "EQUALS" }
  ]
}
```

**Operators:** `EQUALS`, `CONTAINS`, `STARTS_WITH`, `ENDS_WITH`

### Blacklisted Events

Events that won't interrupt interaction matching:

```json
{
  "globalBlacklistedEvents": [
    { "name": "ad_impression", "isBlacklisted": true }
  ]
}
```

### Timeout Handling

If `thresholdInMs` is exceeded between events, the interaction restarts:

```
thresholdInMs = 30s
cart_viewed → (35s) → payment_entered ❌ Timeout - restart
```

---

## Architecture

### Module Structure

```
interaction/
├── core/       # InteractionManager, EventsTracker, EventQueue
├── remote/     # API client, models (ApiResponse, InteractionConfig)
└── library/    # InteractionInstrumentation (OpenTelemetry integration)
```

### Data Flow

```
PulseSDK.trackEvent() 
  → Interaction Instrumentation (listener)
  → InteractionManager 
  → Event matching against API configs
  → Span creation on sequence completion
```

---

## Debugging

### Logs (Debug Builds)

```
Initializing with endpoint: http://10.0.2.2:8080/v1/interaction-configs
Loaded 3 interaction(s)
Initialization complete
```

### Common Issues

| Problem | Solution |
|---------|----------|
| Interactions not loading | Check network connectivity and `baseUrl` |
| Events not matching | Verify event names (case-sensitive), check timeout |
| Spans not appearing | Verify `endpointBaseUrl`, check network permissions |
