# Pulse iOS Example App

This is an example iOS app that demonstrates how to use the Pulse iOS SDK (`PulseIOSSDK`) with a local package dependency.

## Setup

1. **Generate Xcode Project** (if using xcodegen):
   ```bash
   cd Examples/PulseIOSExample
   xcodegen generate
   ```

2. **Open in Xcode**:
   ```bash
   open PulseIOSExample.xcodeproj
   ```

3. **Configure Endpoint**:
   - The app is configured to send traces to `http://127.0.0.1:4318` by default
   - Update the endpoint in `AppDelegate.swift` if needed

## Features Demonstrated

- ✅ SDK Initialization
- ✅ Track Custom Events
- ✅ Track Non-Fatal Errors
- ✅ Track Spans (Closure-based)
- ✅ Start/End Spans (Manual)
- ✅ Network Request Instrumentation

## Local Package Dependency

This app uses the local `pulse-ios` package via a relative path dependency:

```yaml
packages:
  PulseIOS:
    path: ../../
    product: PulseIOSSDK
```

This allows you to test changes to the SDK locally without publishing to a remote repository.

## Running the App

1. Make sure you have an OTLP collector running on `http://127.0.0.1:4318`
2. Build and run the app in Xcode
3. Tap the buttons to test different SDK features
4. Check your collector to see the traces being sent

