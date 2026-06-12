# Interaction Instrumentation

**Track and measure user flows across multiple events in your iOS application.**

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

```swift
Pulse.shared.initialize(
    apiKey: "your-project-id",
    dataCollectionState: .allowed,
    instrumentations: { config in
        config.interaction { interactionConfig in
            interactionConfig.enabled(true)
        }
    }
)
```

**Network Configuration:**

- Simulator: `http://10.0.2.2:8080/` (default)
- Production: `https://api.yourservice.com/`

### 2. Track Events

```swift
Pulse.shared.trackEvent(
    name: "cart_viewed",
    observedTimeStampInMs: Int64(Date().timeIntervalSince1970 * 1000),
    params: ["itemCount": 3]
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
GET {configUrl}/v1/interactions/all-active-interactions
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

| Field                     | Type   | Description                          |
| ------------------------- | ------ | ------------------------------------ |
| `name`                    | String | Interaction name (used as span name) |
| `events`                  | Array  | Ordered sequence of events to match  |
| `globalBlacklistedEvents` | Array  | Events to ignore during matching     |
| `uptimeLowerLimitInMs`    | Long   | Fast interaction threshold           |
| `uptimeMidLimitInMs`      | Long   | Normal interaction threshold         |
| `uptimeUpperLimitInMs`    | Long   | Slow interaction threshold           |
| `thresholdInMs`           | Long   | Max time between events (timeout)    |

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

```swift
class CheckoutViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        Pulse.shared.trackEvent(
            name: "cart_viewed",
            observedTimeStampInMs: timestamp
        )
    }

    func onPaymentSubmit() {
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        Pulse.shared.trackEvent(
            name: "payment_entered",
            observedTimeStampInMs: timestamp,
            params: ["paymentMethod": "credit_card"]
        )
    }

    func onOrderSuccess(orderId: String) {
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        Pulse.shared.trackEvent(
            name: "order_placed",
            observedTimeStampInMs: timestamp,
            params: ["orderId": orderId]
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

## Architecture

### Data Flow

```
PulseKit.trackEvent()
  → OpenTelemetry Log
  → InteractionLogListener (LogRecordProcessor)
  → InteractionManager
  → Event matching against API configs
  → Span creation on sequence completion
```

---

## Debugging

### Common Issues

| Problem                  | Solution                                                                              |
| ------------------------ | ------------------------------------------------------------------------------------- |
| Interactions not loading | Check network connectivity  |
| Events not matching      | Verify event names (case-sensitive), check timeout                                    |
| Spans not appearing      | Verify API key / network permissions; interaction config URL comes from remote config |
