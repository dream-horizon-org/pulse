<p align="center">
  <h1 align="center">Pulse React Native SDK</h1>
</p>

<p align="center">
  <strong>Production-grade observability for React Native applications</strong>
</p>

<p align="center">
  Real-time monitoring, error tracking, and performance insights powered by OpenTelemetry
</p>

<p align="center">
  <code>@dreamhorizonorg/pulse-react-native</code>
</p>

---

## Features

 - 🚨 **Error Monitoring**: Capture JavaScript crashes and exceptions with full stack traces.

 - ⚡ **Performance Monitoring**: Distributed tracing spans for synchronous and asynchronous operations with automatic or manual instrumentation.

 - 🌐 **Network Monitoring**: Auto-instrument HTTP requests (fetch and XMLHttpRequest) with zero code changes.

 - 🧭 **Navigation Tracking**: Automatic screen transition monitoring with React Navigation integration.

 - 📊 **Event Tracking**: Log custom business events and user actions with structured metadata.

 - 🔌 **OpenTelemetry Native**: Built on OpenTelemetry Android SDK. Automatically captures ANR, frozen frames, activity/fragment lifecycle, network changes, view interactions, and more. See the [Android SDK documentation](../pulse-android-otel) for all native features.

 - 🏗️ **Architecture Support**: Supports both **React Native Old Architecture** and **New Architecture** out of the box.

> **Note:** Currently supports Android only. iOS support is coming soon.

---

## Quick Start

### Installation

```sh
npm install @dreamhorizonorg/pulse-react-native
# or
yarn add @dreamhorizonorg/pulse-react-native
```

### Initialization

#### 1. Android Native Setup

Initialize the Pulse Android SDK in your `MainApplication.kt`:

```kotlin
import android.app.Application
import com.pulse.android.sdk.PulseSDK

class MainApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    
    // Initialize Pulse Android SDK
    PulseSDK.INSTANCE.initialize(
      application = this,
      endpointBaseUrl = <server-url>
    )
  }
}
```

> **Important:** This step is mandatory. Without native SDK initialization, no telemetry will be sent.

**Advanced Configuration:**  
For custom endpoints, headers, disk buffering, session settings, ANR thresholds, and other native features, see the [Android SDK Initialization Guide](../pulse-android-otel#initialization).

> **Note:** iOS support is coming soon.

#### 2. React Native Auto-Instrumentation

Enable automatic instrumentation in your app entry point (e.g., `App.tsx`):

```typescript
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

// Enable auto-instrumentation features
Pulse.start();

function App() {
  // Your app code
}
```

**What gets automatically tracked:**
- ✅ JavaScript crashes and unhandled exceptions
- ✅ HTTP requests via fetch and XMLHttpRequest

> **Note:** All `autoInstrument*` options are enabled by default. You can disable specific features by setting them to `false`.

---

## Usage Guide

### Auto-Instrumentation

Pulse React Native automatically captures critical application events without requiring manual instrumentation. Simply call `Pulse.start()` to enable automatic monitoring:

```typescript
Pulse.start(); // All auto-instrumentation enabled by default
```

#### What Gets Automatically Tracked

**1. Unhandled JavaScript Errors**

All unhandled JavaScript exceptions and promise rejections are automatically captured with full stack traces. This includes:
- Runtime errors and crashes
- Unhandled promise rejections
- Fatal and non-fatal exceptions
- Complete JavaScript call stacks

The error handler integrates seamlessly with React Native's `ErrorUtils`, preserving the original error handling chain while capturing telemetry.

**2. Network Requests**

HTTP requests are automatically instrumented when using standard APIs:
- `fetch()` API calls
- `XMLHttpRequest` (XHR) operations

Each network request captures:
- HTTP method (GET, POST, etc.)
- Request URL (full URL, scheme, host, path)
- Response status code
- Request type (fetch or xmlhttprequest)
- Platform (Android/iOS)
- Network errors with error messages and stack traces

**3. React Navigation**

Screen navigation events are tracked when using [React Navigation](https://reactnavigation.org/) (requires setup, see [React Navigation Integration](#react-navigation-integration)):
- Screen-to-screen transitions
- Screen names and route parameters
- Navigation history and patterns

#### Disabling Auto-Instrumentation

You can selectively disable specific auto-instrumentation features:

```typescript
Pulse.start({
  autoDetectExceptions: false,    // Disable crash tracking
  autoDetectNetwork: false,       // Disable network monitoring
  autoDetectNavigation: false     // Disable navigation tracking
});
```

> **Note:** Disabling auto-instrumentation means you'll need to manually track these events if needed.

### React Navigation Integration

Pulse automatically tracks screen navigation and route changes when using [React Navigation](https://reactnavigation.org/).

#### Setup

```typescript
import { NavigationContainer } from '@react-navigation/native';
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

function App() {
  const navigationIntegration = Pulse.createNavigationIntegration();

  return (
    <NavigationContainer
      onReady={(ref) => navigationIntegration.registerNavigationContainer(ref)}
    >
      {/* Your screens */}
    </NavigationContainer>
  );
}
```

#### Supported Navigators

- **[Stack Navigator](https://reactnavigation.org/docs/stack-navigator)** - JavaScript-based stack navigation
- **[Native Stack Navigator](https://reactnavigation.org/docs/native-stack-navigator)** - Native platform navigation

#### Captured Data

Each navigation event includes:

```typescript
{
  'screen.name': 'ProfileScreen',           // Current screen
  'last.screen.name': 'HomeScreen',        // Previous screen
  'routeHasBeenSeen': false,               // First visit or returning
  'routeKey': 'ProfileScreen-abc123',      // Unique route identifier
  'pulse.type': 'screen_load',             // Event type
  'phase': 'start'                         // Navigation phase
}
```

**Requirements:**
- `@react-navigation/native` v5.x or higher
- `autoDetectNavigation: true` in `Pulse.start()` (enabled by default)

### Reporting Unhandled Errors

Unhandled JavaScript errors and promise rejections are automatically captured when `autoDetectExceptions` is enabled (default).

#### Automatic Capture

All uncaught errors are reported with:
- Full JavaScript stack traces
- Error type and message
- Platform information
- Timestamp and context

#### Manual Error Reporting

You can manually report caught exceptions:

```typescript
try {
  await riskyOperation();
} catch (error) {
  Pulse.reportException(error);
}
```

**Report as fatal:**

```typescript
Pulse.reportException(error, true);  // Second parameter: isFatal
```

**With custom attributes:**

```typescript
Pulse.reportException(error, false, {
  userId: '123',
  operation: 'checkout',
  attempt: 3,
  environment: 'production'
});
```

**Supported attribute types:** `string`, `number`, `boolean`, and arrays of these types.

### Reporting Render Errors

Pulse provides a built-in `ErrorBoundary` component that uses [React's Error Boundary API](https://react.dev/reference/react/Component#catching-rendering-errors-with-an-error-boundary) to automatically catch and report errors from inside a React component tree.

#### Basic Usage

```typescript
import { Pulse } from '@dreamhorizonorg/pulse-react-native';
import { View, Text } from 'react-native';

function App() {
  return (
    <Pulse.ErrorBoundary fallback={<Text>Something went wrong</Text>}>
      {/* Your app */}
    </Pulse.ErrorBoundary>
  );
}
```

#### Fallback Component

You can provide a custom fallback UI with error details:

```typescript
function ErrorFallback({ error, componentStack }) {
  return (
    <View>
      <Text>An error occurred</Text>
      <Text>{error.toString()}</Text>
    </View>
  );
}

function App() {
  return (
    <Pulse.ErrorBoundary fallback={ErrorFallback}>
      {/* Your app */}
    </Pulse.ErrorBoundary>
  );
}
```

#### Custom Error Handler

Use the `onError` callback for additional error handling logic:

```typescript
<Pulse.ErrorBoundary 
  fallback={ErrorFallback}
  onError={(error, componentStack) => {
    // Custom logging or side effects
    console.log('Render error:', error);
    console.log('Component stack:', componentStack);
  }}
>
  {/* Your app */}
</Pulse.ErrorBoundary>
```

#### Higher-Order Component (HOC)

Wrap individual components using the HOC pattern:

```typescript
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

const MyComponent = () => {
  // Component code
};

export default Pulse.withErrorBoundary(MyComponent, {
  fallback: <Text>Error occurred</Text>,
  onError: (error, componentStack) => {
    console.log('Component error:', error);
  }
});
```

**How it works:**
- Errors are automatically reported to Pulse with full stack traces
- If a `fallback` is provided, the error is marked as handled (non-fatal)
- If no `fallback` is provided, the error is marked as unhandled (fatal)

> **Note:** In development mode, React will rethrow errors caught by error boundaries. This may result in errors being reported twice. We recommend testing error boundaries with production builds.

### Reporting ANRs and Frozen Frames

ANR (Application Not Responding) and frozen frame detection are automatically handled by the native Android SDK.

**What's detected:**
- **Android ANRs** - When the main thread blocks for too long
- **Frozen Frames** - When rendering takes longer than expected
- **Slow Renders** - UI performance bottlenecks

**Configuration:**  
ANR and frozen frame detection are enabled by default. To customize thresholds, detection intervals, or disable specific checks, see the Android SDK documentation:
- [ANR Detection](../pulse-android-otel/instrumentation/anr)
- [Slow Rendering Detection](../pulse-android-otel/instrumentation/slowrendering)

### Tracking CodePush Deployments

If your app uses over-the-air (OTA) updates like [Microsoft App Center CodePush](https://github.com/microsoft/react-native-code-push) or [Delivr DOTA](https://github.com/ds-horizon/delivr-sdk-ota), you can track which JavaScript bundle version is running by setting a global attribute.

#### Why Track Code Bundle IDs?

With OTA updates, the same native app version can run multiple different JavaScript bundles. Tracking the code bundle ID helps you:
- **Identify deployment-specific issues**: Pinpoint which OTA release caused an error
- **Track update adoption**: See how many users are on each bundle version
- **Debug effectively**: Match errors to the exact code version and source maps

#### Setting Code Bundle ID

Use `Pulse.setGlobalAttribute()` to pass the code bundle ID from your OTA provider:

```typescript
import codePush from '@d11/dota';
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

// Set on app start
codePush.getUpdateMetadata().then(update => {
  if (update?.label) {
    Pulse.setGlobalAttribute('codeBundleId', update.label);
  }
});
```

All subsequent events, errors, and spans will automatically include the `codeBundleId` attribute, allowing you to filter and analyze telemetry data by deployment version.

> **Tip:** For best results, set the code bundle ID before calling `Pulse.start()` to ensure all telemetry includes this identifier.

### Setting Global Attributes

Global attributes are automatically attached to all telemetry data (events, errors, spans) throughout your application's lifecycle.

> **Note:** Global attributes set via `setGlobalAttribute` only apply to telemetry originating from the React Native side. Native Android events (ANR, frozen frames, activity lifecycle, etc.) are not affected. To set global attributes for native Android telemetry, refer to the [Pulse Android SDK initialization guide](https://github.com/ds-horizon/pulse/tree/feat/interaction-reactive/pulse-android-otel#agent-initialization).

#### Basic Usage

```typescript
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

// Set global attributes
Pulse.setGlobalAttribute('environment', 'production');
Pulse.setGlobalAttribute('buildNumber', '1234');

// All subsequent telemetry will include these attributes
Pulse.trackEvent('user_login', { userId: '123' });
```

#### Practical Example

```typescript
import DeviceInfo from 'react-native-device-info';
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

// Set once at app start
Pulse.setGlobalAttribute('appVersion', DeviceInfo.getVersion());
Pulse.setGlobalAttribute('environment', __DEV__ ? 'development' : 'production');
Pulse.setGlobalAttribute('userTier', 'premium');
```

#### Attribute Priority

Event/span-specific attributes override global attributes when keys conflict:

```typescript
Pulse.setGlobalAttribute('environment', 'production');

// This event's 'environment' will be 'staging'
Pulse.trackEvent('test', { environment: 'staging' });
```

**Common use cases:** CodePush labels, build numbers, environment flags, feature flags, user segments, device metadata.

### Setting User Properties

Associate telemetry with specific users:

```typescript
// Set user ID
Pulse.setUserId('user-12345');

// Set individual properties
Pulse.setUserProperty('email', 'user@example.com');
Pulse.setUserProperty('plan', 'premium');

// Set multiple properties at once
Pulse.setUserProperties({
  email: 'user@example.com',
  plan: 'premium',
  signupDate: '2024-01-15',
  verified: true
});

// Clear user on logout
Pulse.setUserId(null);
```

### Custom Instrumentation

Create custom performance traces (spans) to measure the execution time of specific operations in your application. Spans help you understand which parts of your code are slow and need optimization.

#### Understanding Spans

A **span** represents a unit of work or operation. Each span has:
- A **name**: Describes what the span measures (e.g., `'screen_render'`, `'image_upload'`, `'payment_flow'`)
- A **start time**: When the operation began
- An **end time**: When the operation completed
- **Attributes**: Additional context (e.g., `{ screenName: 'Checkout', itemCount: 3 }`)
- **Events**: Milestones within the span (e.g., `'validation_passed'`, `'api_call_started'`, `'ui_rendered'`)

When you start a span, it becomes **active**, meaning it's the current span being tracked. Any errors that occur while a span is active are automatically associated with that span.

#### Automatic Span Management: `trackSpan()` (Recommended)

The `trackSpan()` function automatically manages the span lifecycle for you. It starts the span, executes your code, and ends the span when done—even if an error occurs. This works with both synchronous and asynchronous operations.

**Synchronous operation:**

```typescript
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

const result = Pulse.trackSpan('calculate_total',
  { attributes: { itemCount: 5 } },
  () => {
    let total = 0;
    for (let i = 0; i < 1000; i++) {
      total += i;
    }
    return total;
  }
);
```

**Asynchronous operation:**

```typescript
const users = await Pulse.trackSpan('fetch_users',
  { attributes: { limit: 100 } },
  async () => {
    const response = await fetch('https://api.example.com/users?limit=100');
    return response.json();
  }
);
```

#### Manual Span Control: `startSpan()`

Use `startSpan()` when you need fine-grained control over the span lifecycle. This is useful for:
- Long-running operations with multiple phases
- Operations where you need to add events or update attributes dynamically
- Scenarios where the span end time isn't tied to a single function

**Basic example:**

```typescript
const span = Pulse.startSpan('image_processing', {
  attributes: { imageId: 'img-123', format: 'jpeg' }
});

try {
  span.addEvent('resize_started');
  await resizeImage();
  span.addEvent('resize_completed', { newSize: '800x600' });

  span.addEvent('compression_started');
  await compressImage();
  span.addEvent('compression_completed');

  span.setAttributes({ finalSize: 245000, compressionRatio: 0.65 });
} catch (error) {
  span.recordException(error);
  span.setAttributes({ status: 'failed' });
} finally {
  span.end(); // Always end the span
}
```

**Span API:**

```typescript
import { Pulse, SpanStatusCode } from '@dreamhorizonorg/pulse-react-native';

// Add a single event
span.addEvent('cache_miss');

// Add event with attributes
span.addEvent('retry_attempt', { attemptNumber: 2, delayMs: 1000 });

// Update span attributes
span.setAttributes({ 
  recordsProcessed: 150, 
  cacheHitRate: 0.85 
});

// Record an exception
try {
  await riskyOperation();
} catch (error) {
  span.recordException(error);
  span.end(SpanStatusCode.ERROR); // Mark span as failed
}

// End the span with status
span.end(); // Default: SpanStatusCode.UNSET
span.end(SpanStatusCode.OK); // Explicitly mark as successful
span.end(SpanStatusCode.ERROR); // Mark as failed
```

#### Span Status Codes

You can optionally set a status code when ending a span to indicate the outcome:

| Status | Description | When to Use |
|--------|-------------|-------------|
| `SpanStatusCode.OK` | Operation completed successfully | Successful operations, no errors |
| `SpanStatusCode.ERROR` | Operation failed or encountered an error | Failures, exceptions, validation errors |
| `SpanStatusCode.UNSET` | Status not specified (default) | When outcome is unknown or not applicable |

**Example with status codes:**

```typescript
import { Pulse, SpanStatusCode } from '@dreamhorizonorg/pulse-react-native';

const span = Pulse.startSpan('payment_processing');

try {
  await processPayment();
  span.end(SpanStatusCode.OK); // Success
} catch (error) {
  span.recordException(error);
  span.end(SpanStatusCode.ERROR); // Failure
}
```

#### Best Practices

1. **Use descriptive span names**: `'fetch_user_profile'` is better than `'api_call'`
2. **Add meaningful attributes**: Include IDs, counts, types, or other context
3. **Always end manual spans**: Use `try/finally` to ensure `span.end()` is called
4. **Record exceptions**: Call `span.recordException(error)` to capture errors
5. **Add events for milestones**: Track key points like `'cache_hit'`, `'retry_started'`, `'validation_passed'`

> **Note:** Spans created with `startSpan()` must be manually ended with `span.end()`. Forgetting to end a span will result in incomplete telemetry data.

### Tracking Custom Events

Track custom business events, user actions, and application milestones to gain insights into user behavior and application usage patterns.

#### Basic Usage

```typescript
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

// Simple event without attributes
Pulse.trackEvent('app_opened');
Pulse.trackEvent('user_logout');
```

#### Events with Attributes

Add context to your events with attributes:

```typescript
// E-commerce events
Pulse.trackEvent('product_viewed', {
  productId: 'SKU-12345',
  category: 'electronics',
  price: 299.99,
  inStock: true
});
```

---

## API Reference

### Initialization

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `Pulse.start(options?)` | `options?: PulseStartOptions` | `void` | Initialize auto-instrumentation. All options default to `true`. |
| `Pulse.isInitialized()` | - | `boolean` | Check if native SDK is initialized. |

**PulseStartOptions:**
```typescript
{
  autoDetectExceptions?: boolean;    // Auto-detect JS crashes & errors
  autoDetectNavigation?: boolean;    // Auto-detect navigation
  autoDetectNetwork?: boolean;       // Auto-detect HTTP requests
}
```

---

### Error Tracking

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `Pulse.reportException(error, isFatal?, attributes?)` | `error: Error \| string`<br/>`isFatal?: boolean`<br/>`attributes?: PulseAttributes` | `void` | Report an exception. Default `isFatal: false`. |

---

### Events

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `Pulse.trackEvent(event, attributes?)` | `event: string`<br/>`attributes?: PulseAttributes` | `void` | Track a custom event with optional attributes. |

---

### Performance Tracing

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `Pulse.trackSpan(name, options, fn)` | `name: string`<br/>`options: { attributes?: PulseAttributes }`<br/>`fn: () => T \| Promise<T>` | `T \| Promise<T>` | Auto-managed span. Returns function result. |
| `Pulse.startSpan(name, options?)` | `name: string`<br/>`options?: { attributes?: PulseAttributes }` | `Span` | Create a span with manual control. |

**Span Interface:**
```typescript
interface Span {
  spanId: string;
  end(statusCode?: SpanStatusCode): void;
  addEvent(name: string, attributes?: PulseAttributes): void;
  setAttributes(attributes?: PulseAttributes): void;
  recordException(error: Error, attributes?: PulseAttributes): void;
}

// Span status codes
const SpanStatusCode = {
  OK: 'OK',
  ERROR: 'ERROR',
  UNSET: 'UNSET',
} as const;

type SpanStatusCode = typeof SpanStatusCode[keyof typeof SpanStatusCode];
```

---

### User Identification

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `Pulse.setUserId(id)` | `id: string \| null` | `void` | Set user ID. Pass `null` to clear. |
| `Pulse.setUserProperty(name, value)` | `name: string`<br/>`value: string \| null` | `void` | Set a single user property. |
| `Pulse.setUserProperties(properties)` | `properties: PulseAttributes` | `void` | Set multiple user properties. |

---

### Global Attributes

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `Pulse.setGlobalAttribute(key, value)` | `key: string`<br/>`value: string` | `void` | Set attribute for all events/spans. Pass empty string to remove. |

---

### Error Boundary

| Component/HOC | Props | Description |
|---------------|-------|-------------|
| `<Pulse.ErrorBoundary>` | `fallback?: React.ReactElement \| FallbackRender`<br/>`onError?: (error, componentStack) => void` | Catch React render errors. |
| `Pulse.withErrorBoundary(Component, options)` | `Component: React.ComponentType`<br/>`options: ErrorBoundaryProps` | HOC to wrap component with error boundary. |

---

### Type Definitions

```typescript
// Attribute values - primitives and homogeneous arrays
type PulseAttributeValue =
  | string
  | number
  | boolean
  | Array<null | undefined | string>
  | Array<null | undefined | number>
  | Array<null | undefined | boolean>;

// Attributes object
type PulseAttributes = Record<string, PulseAttributeValue | undefined>;
```

---

## Troubleshooting

### ⚠️ Warning: "SDK not initialized"

**Symptoms:**

JavaScript console:
```
⚠️ [Pulse RN] Events will not be sent - SDK not initialized.
Call Pulse.start() and initialize PulseSDK in MainApplication.kt
```

Android logcat:
```
W/PulseLogger: PulseSDK not initialized. Events will not be tracked...
```

**Cause:** Native Android SDK not initialized.

**Solution:**

1. Add to `MainApplication.kt`:
```kotlin
override fun onCreate() {
  super.onCreate()
  PulseSDK.INSTANCE.initialize(this)
}
```

2. Verify initialization:
```typescript
console.log('Pulse ready:', Pulse.isInitialized());
```

3. Clean rebuild:
```bash
cd android && ./gradlew clean
cd .. && npx react-native run-android
```

### Network Requests Not Tracked

**Possible causes:**
- `autoInstrumentNetwork: false` in `Pulse.start()`
- Using unsupported HTTP library (only fetch/XHR supported)
- Network module initialization race condition

**Solution:**
```typescript
Pulse.start({ autoInstrumentNetwork: true });
```

### Navigation Not Tracked

**Possible causes:**
- `autoInstrumentNavigation: false` in `Pulse.start()`
- Navigation container not registered
- Using non-React Navigation library

**Solution:**
```typescript
Pulse.start({ autoInstrumentNavigation: true });

const integration = Pulse.createNavigationIntegration();
<NavigationContainer onReady={integration.registerNavigationContainer}>
```

---

## Best Practices

### 1. Initialize Early

Initialize the native SDK as early as possible in your app lifecycle:

```kotlin
class MainApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    PulseSDK.INSTANCE.initialize(this)  // First thing
    // ... other initializations
  }
}
```

### 2. Set User Context Immediately

Set user information right after authentication:

```typescript
async function handleLogin(credentials) {
  const user = await login(credentials);
  
  // Set user context immediately
  Pulse.setUserId(user.id);
  Pulse.setUserProperties({
    email: user.email,
    plan: user.subscription,
    signupDate: user.createdAt
  });
}
```

### 3. Use Global Attributes for Static Metadata

Set global attributes for information that doesn't change during the session:

```typescript
Pulse.start();

// Set once at app start
Pulse.setGlobalAttribute('appVersion', DeviceInfo.getVersion());
Pulse.setGlobalAttribute('buildType', __DEV__ ? 'debug' : 'release');
Pulse.setGlobalAttribute('deviceModel', DeviceInfo.getModel());
```

### 4. Prefer `trackSpan()` Over `startSpan()`

Use automatic span management unless you need fine-grained control:

```typescript
// ✅ Preferred - automatic lifecycle
await Pulse.trackSpan('operation', {}, async () => {
  await doWork();
});

// ⚠️ Use only when needed - manual lifecycle
const span = Pulse.startSpan('operation');
await doWork();
span.end();  // Easy to forget!
```

### 5. Add Context to Errors

Always include relevant context when reporting errors:

```typescript
try {
  await syncData();
} catch (error) {
  Pulse.reportException(error, false, {
    operation: 'data_sync',
    userId: currentUser?.id,
    lastSyncTime: lastSync.toISOString(),
    itemsPending: pendingItems.length
  });
}
```

---

## Requirements

- **React Native:** ≥ 0.70
- **Android:** minSdk ≥ 24, compileSdk ≥ 35
- **Dependencies:**
 - [Pulse Android SDK](../pulse-android-otel)

---

## License

MIT

---

Made with ❤️ using [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
