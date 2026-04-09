# Crash Instrumentation

The Crash instrumentation detects native crashes and reports them as OpenTelemetry log events.

## Crash types

Crash capture is powered by [KSCrash](https://github.com/kstenerud/KSCrash). KSCrash handles:

- Mach kernel exceptions
- Fatal signals
- C++ exceptions
- Objective-C exceptions
- Main thread deadlock (experimental)
- Custom crashes (e.g. from scripting languages)

For detailed functionality and behavior, see [KSCrash](https://github.com/kstenerud/KSCrash).

## Attributes

When a crash is detected and the app relaunches, the following attributes are emitted on the `device.crash` log event:

| Attribute | Description |
|-----------|-------------|
| `exception.type` | Exception or signal name (e.g. `NSRangeException`, `SIGABRT`, `EXC_BAD_ACCESS`) |
| `exception.message` | Exception reason or crash diagnosis |
| `exception.stacktrace` | Full Apple-format crash report |
| `thread.id` | Index of the crashed thread |
| `thread.name` | Thread name or dispatch queue label |

These follow the [OpenTelemetry Exception semantic conventions](https://opentelemetry.io/docs/specs/semconv/registry/attributes/exception/) and [Thread semantic conventions](https://opentelemetry.io/docs/specs/semconv/registry/attributes/thread/).

## Key points

- Crash capture and report emission happen in different app launches — KSCrash handles the at-crash write, Pulse handles the next-launch read.
- The session active at crash time is preserved via `KSCrash.shared.userInfo` and stitched back into the emitted log, so the crash is attributed to the correct session, not the current one.
- If report processing fails, reports stay on disk and are retried on the next launch.

## Testing crashes

KSCrash's signal and Mach handlers are intercepted by LLDB, so crashes must be triggered without the debugger attached.

1. Open `PulseIOSExample` in Xcode and **Build & Run** (this installs the app on the simulator/device). (Make sure app is not debug executable as it may not crash the app).
2. Tap the app icon on the simulator/device to launch.
3. Scroll to the **Crash Testing** section and tap any crash button:
   - NSException (Obj-C)
   - Fatal Error (Swift)
4. Tap the app icon again — the crash report will be processed and emitted as a `device.crash` log event
6. Check your backend for the emitted crash attributes
