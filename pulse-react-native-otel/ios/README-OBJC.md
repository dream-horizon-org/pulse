# Pulse — Objective-C Usage

Import the Swift header in your `.m` / `.mm` file:

```objc
#import <PulseReactNativeOtel-Swift.h>
```

---

## Initialize

Call from `AppDelegate` before anything else.

```objc
[PulseSDK pulseInitialize:@"YOUR_API_KEY"
    dataCollectionState:@"PENDING"
       globalAttributes:nil
          configuration:nil
        instrumentations:nil];
```

**With config:**

```objc
NSDictionary<NSString *, PulseAttributeValue *> *attrs = @{
    @"env": [PulseAttributeValue string:@"production"],
    @"version": [PulseAttributeValue string:@"2.1.0"],
};

PulseObjcKitConfiguration *kit = [PulseObjcKitConfiguration new];
kit.includeScreenAttributes = @YES;

PulseObjcInstrumentations *inst = [PulseObjcInstrumentations new];
inst.crash = [PulseObjcEnabledConfig enabled];
inst.urlSession = [PulseObjcEnabledConfig disabled];

[PulseSDK pulseInitialize:@"YOUR_API_KEY"
    dataCollectionState:@"ALLOWED"
       globalAttributes:attrs
          configuration:kit
        instrumentations:inst];
```

`dataCollectionState` accepts `@"ALLOWED"`, `@"DENIED"`, or `@"PENDING"`.

---

## API Reference

**Identity**
```objc
[PulseSDK pulseSetUserId:@"user-123"];
[PulseSDK pulseSetUserProperty:@"plan" value:[PulseAttributeValue string:@"pro"]];
[PulseSDK pulseSetUserProperties:@{ @"plan": [PulseAttributeValue string:@"pro"] }];
```

**Events**
```objc
[PulseSDK pulseTrackEvent:@"button_tapped"
    observedTimeStampInMs:timestampMs
                   params:@{ @"screen": [PulseAttributeValue string:@"home"] }];
```

**Non-fatals**
```objc
// By name
[PulseSDK pulseTrackNonFatal:@"checkout_failed"
       observedTimeStampInMs:timestampMs
                      params:@{}];

// By NSError
[PulseSDK pulseTrackNonFatalError:error
           observedTimeStampInMs:timestampMs
                          params:@{}];
```

**Consent**
```objc
[PulseSDK pulseSetDataCollectionState:@"ALLOWED"];
```

**Utilities**
```objc
BOOL ready = [PulseSDK pulseIsInitialized];
BOOL stopped = [PulseSDK shutdown];
NSDictionary *features = [PulseSDK pulseGetAllFeatures]; // nil if config not loaded yet
```

---

## PulseAttributeValue

```objc
[PulseAttributeValue string:@"value"]
[PulseAttributeValue int:42]
[PulseAttributeValue double:3.14]
[PulseAttributeValue bool:YES]
[PulseAttributeValue stringArray:@[@"a", @"b"]]
```

---

## Configuring Instrumentations

All instrumentation config goes into a `PulseObjcInstrumentations` object passed to `pulseInitialize`.
Omitting a field (leaving it `nil`) keeps the SDK default for that instrumentation.

### URL Session
```objc
inst.urlSession = [PulseObjcEnabledConfig enabled];
// or
inst.urlSession = [PulseObjcEnabledConfig disabled];
```

### Crash Reporting
```objc
inst.crash = [PulseObjcEnabledConfig enabled];
```

### Sessions
```objc
PulseObjcSessionsConfig *sessions = [PulseObjcSessionsConfig enabled];
sessions.maxLifetimeSeconds = @(1800);
sessions.backgroundInactivityTimeoutSeconds = @(300);
sessions.shouldPersist = @YES;
inst.sessions = sessions;
```

### UIKit Tap (Interaction)
```objc
PulseObjcUIKitTapConfig *tap = [PulseObjcUIKitTapConfig new];
tap.enabled = @YES;
tap.captureContext = @YES;

PulseObjcRageConfig *rage = [PulseObjcRageConfig new];
rage.timeWindowMs = @(400);
rage.rageThreshold = @(3);
rage.radiusPt = @(20.0);
tap.rage = rage;

inst.uiKitTap = tap;
```

### Session Replay
```objc
PulseObjcSessionReplayConfig *replay = [PulseObjcSessionReplayConfig new];
replay.enabled = @YES;
// Note: Privacy, quality, and flush settings are now controlled via backend remote config
// Use maskViewClasses / unmaskViewClasses for code-level view masking only
replay.maskViewClasses = @[@"SensitiveView"];
replay.unmaskViewClasses = @[@"PublicBannerView"];
inst.sessionReplay = replay;
```

### App Lifecycle
```objc
inst.appLifecycle = [PulseObjcEnabledConfig enabled];
```

### Screen Lifecycle
```objc
inst.screenLifecycle = [PulseObjcEnabledConfig enabled];
```

### App Startup
```objc
inst.appStartup = [PulseObjcEnabledConfig enabled];
```

### Location
```objc
inst.location = [PulseObjcEnabledConfig enabled];
```

### SignPost Integration
```objc
inst.signPost = [PulseObjcEnabledConfig enabled];
```

### Interaction
```objc
inst.interaction = [PulseObjcEnabledConfig enabled];
```

---

## Configuring PulseObjcKitConfiguration

Controls which attribute groups are attached to all signals. Pass as the `configuration` argument to `pulseInitialize`.

```objc
PulseObjcKitConfiguration *kit = [PulseObjcKitConfiguration new];
kit.includeScreenAttributes = @YES;   // attach current screen name to every signal
kit.includeNetworkAttributes = @YES;  // attach network info (reachability, carrier)
kit.includeGlobalAttributes = @YES;   // attach global attributes set via setUserProperty / setUserProperties
```

Omitting any field (leaving it `nil`) keeps the SDK default.

---

## Notes

- `startSpan`, `trackSpan`, and `getOpenTelemetry` are Swift-only — OTel types are not ObjC-compatible.
