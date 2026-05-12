/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.activity.startup;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.opentelemetry.android.common.RumConstants;
import io.opentelemetry.android.internal.services.visiblescreen.activities.DefaultingActivityLifecycleCallbacks;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.common.Clock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AppStartupTimer {
    // Maximum time from app start to creation of the UI. If this time is exceeded we will not
    // create the app start span. Long app startup could indicate that the app was really started in
    // background, in which case the measured startup time is misleading.
    private static final long MAX_TIME_TO_UI_INIT = TimeUnit.MINUTES.toNanos(1);

    // --------------------------------------------------------------------------------------------
    // Static instance holder (singleton-by-convention).
    //
    // Set in start() and cleared in reportFullyDrawn() (one-shot) or discarded if the method
    // was never called (tiny memory cost, acceptable for a single-use per process lifetime).
    // --------------------------------------------------------------------------------------------

    @Nullable private static volatile AppStartupTimer CURRENT_INSTANCE = null;

    /**
     * Returns the active AppStartupTimer for this process, or {@code null} if
     * {@link #reportFullyDrawn()} was already called or {@link #start} was never invoked.
     */
    @Nullable
    public static AppStartupTimer getInstance() {
        return CURRENT_INSTANCE;
    }

    // --------------------------------------------------------------------------------------------
    // Instance state
    // --------------------------------------------------------------------------------------------

    private final AnchoredClock startupClock = AnchoredClock.create(Clock.getDefault());

    /**
     * <p>API 24+: derived from {@code Process.getStartUptimeMillis()} (OS process fork time).
     * API &lt;24, or if conversion is out of range: falls back to SDK-init clock time.
     */
    private final long firstPossibleTimestamp;

    /**
     * Tracer stored in {@link #start} so that {@link #reportFullyDrawn()} can create a span
     * even after {@link #end()} / {@link #clear()} have run.
     */
    @Nullable private volatile Tracer tracer;

    /**
     * Guards the one-shot {@link #reportFullyDrawn()} so concurrent / repeated calls are safe.
     * {@code compareAndSet(false, true)} ensures exactly one span is ever emitted.
     */
    private final AtomicBoolean fullyDrawnReported = new AtomicBoolean(false);

    @Nullable private volatile Span overallAppStartSpan = null;
    @Nullable private volatile Runnable completionCallback = null;

    // accessed only from UI thread
    private boolean uiInitStarted = false;
    // accessed only from UI thread
    private boolean uiInitTooLate = false;
    // accessed only from UI thread
    private boolean isStartedFromBackground = false;

    public AppStartupTimer() {
        this.firstPossibleTimestamp = resolveProcessStartTimestamp(startupClock);
    }

    // --------------------------------------------------------------------------------------------
    // OS-level process-start timestamp resolution
    // --------------------------------------------------------------------------------------------

    /**
     * Returns the best-available process-start epoch timestamp in nanoseconds.
     *
     * <p>API ≥24: reads {@code Process.getStartUptimeMillis()} (OS fork time)
     * <p>Sanity guard: rejects timestamps that are in the future or more than 10 minutes in the
     * past (both indicate a clock anomaly on some devices / after suspend/resume).
     * <p>Fallback: returns {@code clock.now()} — the SDK-init time — which was the original
     * pre-OS-anchor behaviour.
     */
    private static long resolveProcessStartTimestamp(AnchoredClock clock) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N /* API 24 */) {
            try {
                long processStartUptimeMs = getProcessStartUptimeMillisApi24();
                long currentUptimeMs = SystemClock.uptimeMillis();
                long currentEpochNanos = clock.now();

                long deltaMs = currentUptimeMs - processStartUptimeMs;
                long deltaNanos = TimeUnit.MILLISECONDS.toNanos(deltaMs);

                long tenMinutesNanos = TimeUnit.MINUTES.toNanos(10);
                if (deltaNanos < 0 || deltaNanos > tenMinutesNanos) {
                    Log.d(RumConstants.OTEL_RUM_LOG_TAG,
                        "[Pulse AppStart] OS process-start delta out of range (" + deltaNanos
                            + " ns); falling back to SDK-init time");
                    return clock.now();
                }

                long processStartEpochNanos = currentEpochNanos - deltaNanos;
                Log.d(RumConstants.OTEL_RUM_LOG_TAG,
                    "[Pulse AppStart] OS process-start resolved: deltaMs=" + deltaMs
                        + " processStartEpochNanos=" + processStartEpochNanos);
                return processStartEpochNanos;

            } catch (Exception e) {
                Log.d(RumConstants.OTEL_RUM_LOG_TAG,
                    "[Pulse AppStart] OS process-start unavailable; using SDK-init time. cause=" + e);
            }
        } else {
            Log.d(RumConstants.OTEL_RUM_LOG_TAG,
                "[Pulse AppStart] API < 24; Process.getStartUptimeMillis() unavailable; using SDK-init time");
        }
        return clock.now();
    }

    /**
     * Isolated wrapper for {@code Process.getStartUptimeMillis()} (API 24).
     *
     * <p>AnimalSniffer is configured via
     * {@code animalsniffer { annotation = "androidx.annotation.RequiresApi" }} to skip API
     * checks on methods carrying this annotation. The runtime guard in
     * {@link #resolveProcessStartTimestamp} ensures we never reach here below API 24.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.N)
    private static long getProcessStartUptimeMillisApi24() {
        return Process.getStartUptimeMillis();
    }

    // --------------------------------------------------------------------------------------------
    // AppStart span lifecycle
    // --------------------------------------------------------------------------------------------

    /**
     * Starts the AppStart span and registers this timer as the process-wide current instance.
     * <p>Idempotent: subsequent calls are ignored if the span is already in flight.
     */
    public Span start(Tracer tracer) {
        if (overallAppStartSpan != null) {
            return overallAppStartSpan;
        }

        // Store tracer for later use in reportFullyDrawn(), which may be called after end().
        this.tracer = tracer;

        final Span appStart =
                tracer.spanBuilder("AppStart")
                        .setStartTimestamp(firstPossibleTimestamp, TimeUnit.NANOSECONDS)
                        .setAttribute(RumConstants.START_TYPE_KEY, "cold")
                        .startSpan();
        overallAppStartSpan = appStart;

        // Publish so PulseSDKInternal.reportFullyDrawn() can reach this timer without a
        // direct reference. The instance stays alive until reportFullyDrawn() fires.
        CURRENT_INSTANCE = this;

        return appStart;
    }

    /** Returns the epoch timestamp in nanos calculated by the startupClock. */
    public long clockNow() {
        return startupClock.now();
    }

    /**
     * Creates a lifecycle listener that starts the UI init when an activity is created.
     *
     * @return a new Application.ActivityLifecycleCallbacks instance
     */
    public Application.ActivityLifecycleCallbacks createLifecycleCallback() {
        return new DefaultingActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(
                    @NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                startUiInit();
            }
        };
    }

    private void startUiInit() {
        if (uiInitStarted || isStartedFromBackground) {
            return;
        }
        uiInitStarted = true;
        if (firstPossibleTimestamp + MAX_TIME_TO_UI_INIT < startupClock.now()) {
            Log.d(RumConstants.OTEL_RUM_LOG_TAG, "Max time to UI init exceeded");
            uiInitTooLate = true;
            clear();
        }
    }

    public void setCompletionCallback(Runnable completionCallback) {
        this.completionCallback = completionCallback;
    }

    public void end() {
        Span overallAppStartSpan = this.overallAppStartSpan;
        if (overallAppStartSpan != null && !uiInitTooLate && !isStartedFromBackground) {
            runCompletionCallback();
            overallAppStartSpan.end(startupClock.now(), TimeUnit.NANOSECONDS);
        }
        clear();
    }

    @Nullable
    public Span getStartupSpan() {
        return overallAppStartSpan;
    }

    // visibleForTesting
    public void runCompletionCallback() {
        Runnable completionCallback = this.completionCallback;
        if (completionCallback != null) {
            completionCallback.run();
        }
    }

    private void clear() {
        overallAppStartSpan = null;
        completionCallback = null;
    }

    public void detectBackgroundStart(Handler handler) {
        handler.post(new StartFromBackgroundRunnable(this));
    }

    /**
     * See
     * https://github.com/firebase/firebase-android-sdk/blob/939f90edd74373d42772518d04826657c2ef2e21/firebase-perf/src/main/java/com/google/firebase/perf/metrics/AppStartTrace.java#L283
     * When a runnable posted to main UI thread is executed before any activity's onCreate() method
     * then the app is started in background.
     */
    private static class StartFromBackgroundRunnable implements Runnable {
        private final AppStartupTimer startupTimer;

        private StartFromBackgroundRunnable(AppStartupTimer startupTimer) {
            this.startupTimer = startupTimer;
        }

        @Override
        public void run() {
            if (!startupTimer.uiInitStarted) {
                Log.d(RumConstants.OTEL_RUM_LOG_TAG, "Detected background app start");
                startupTimer.isStartedFromBackground = true;
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // reportFullyDrawn — retroactive AppInteractive span
    // --------------------------------------------------------------------------------------------
    public void reportFullyDrawn() {
        if (!fullyDrawnReported.compareAndSet(false, true)) {
            Log.d(RumConstants.OTEL_RUM_LOG_TAG,
                "[Pulse AppStart] reportFullyDrawn() already called (no-op)");
            return;
        }

        final Tracer t = this.tracer;
        if (t == null) {
            Log.d(RumConstants.OTEL_RUM_LOG_TAG,
                "[Pulse AppStart] reportFullyDrawn() called but tracer is null (SDK not yet started)");
            return;
        }

        // Capture end time BEFORE building the span so the measured duration is accurate.
        final long endNanos = Clock.getDefault().now();
        final long durationMs = TimeUnit.NANOSECONDS.toMillis(endNanos - firstPossibleTimestamp);

        Log.d(RumConstants.OTEL_RUM_LOG_TAG,
            "[Pulse AppStart] reportFullyDrawn() creating AppInteractive span: durationMs=" + durationMs);

        // Build the span with the OS-anchor start timestamp and end it immediately.
        // PulseSdkSignalProcessors.onEnding() will add pulse.type = "app_interactive".
        final Span span = t.spanBuilder("AppInteractive")
                .setStartTimestamp(firstPossibleTimestamp, TimeUnit.NANOSECONDS)
                .startSpan();
        span.end(endNanos, TimeUnit.NANOSECONDS);

        // Clean up static reference — no more use after one-shot fire.
        CURRENT_INSTANCE = null;
    }
}
