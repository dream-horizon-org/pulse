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
    private static final String START_ANCHOR_KEY = "start.anchor";
    private static final String START_ANCHOR_OS = "os";
    private static final String START_ANCHOR_SDK_INIT = "sdk_init";

    /** Set in {@link #start}; cleared in {@link #reportFullyDrawn} so the hook works after {@link #end}. */
    @Nullable private static volatile AppStartupTimer CURRENT_INSTANCE = null;

    @Nullable
    public static AppStartupTimer getInstance() {
        return CURRENT_INSTANCE;
    }

    private final AnchoredClock startupClock = AnchoredClock.create(Clock.getDefault());

    /** OS process-fork epoch-nanos on API 24+; SDK-init time as fallback. */
    private final long firstPossibleTimestamp;
    private final String startAnchorSource;

    /** Held across {@link #end}/{@link #clear} so {@link #reportFullyDrawn} can build the span later. */
    @Nullable private volatile Tracer tracer;

    private final AtomicBoolean fullyDrawnReported = new AtomicBoolean(false);

    @Nullable private volatile Span overallAppStartSpan = null;
    @Nullable private volatile Runnable completionCallback = null;
    // whether activity has been created
    // accessed only from UI thread
    private boolean uiInitStarted = false;
    // whether MAX_TIME_TO_UI_INIT has been exceeded
    // accessed only from UI thread
    private boolean uiInitTooLate = false;
    // accessed only from UI thread
    private boolean isStartedFromBackground = false;

    public AppStartupTimer() {
        StartAnchor anchor = resolveStartAnchor(startupClock);
        this.firstPossibleTimestamp = anchor.epochNanos;
        this.startAnchorSource = anchor.source;
    }

    private static final class StartAnchor {
        final long epochNanos;
        final String source;
        StartAnchor(long epochNanos, String source) {
            this.epochNanos = epochNanos;
            this.source = source;
        }
    }

    /**
     * Resolves the OS process-fork timestamp (API 24+); falls back to {@code clock.now()} on
     * older OS, conversion failures, or out-of-range deltas (future or >10 min in past).
     */
    private static StartAnchor resolveStartAnchor(AnchoredClock clock) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                long deltaNanos = TimeUnit.MILLISECONDS.toNanos(
                    SystemClock.uptimeMillis() - getProcessStartUptimeMillisApi24());
                if (deltaNanos >= 0 && deltaNanos <= TimeUnit.MINUTES.toNanos(10)) {
                    return new StartAnchor(clock.now() - deltaNanos, START_ANCHOR_OS);
                }
                Log.d(RumConstants.OTEL_RUM_LOG_TAG,
                    "[Pulse AppStart] process-start delta out of range; using SDK-init time");
            } catch (Exception e) {
                Log.d(RumConstants.OTEL_RUM_LOG_TAG,
                    "[Pulse AppStart] process-start unavailable; using SDK-init time. cause=" + e);
            }
        }
        return new StartAnchor(clock.now(), START_ANCHOR_SDK_INIT);
    }

    /** API 24 call isolated so AnimalSniffer ({@code @RequiresApi}-aware) skips the check. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.N)
    private static long getProcessStartUptimeMillisApi24() {
        return Process.getStartUptimeMillis();
    }

    public Span start(Tracer tracer) {
        // guard against a double-start and just return what's already in flight.
        if (overallAppStartSpan != null) {
            return overallAppStartSpan;
        }
        this.tracer = tracer;
        final Span appStart =
                tracer.spanBuilder("AppStart")
                        .setStartTimestamp(firstPossibleTimestamp, TimeUnit.NANOSECONDS)
                        .setAttribute(RumConstants.START_TYPE_KEY, "cold")
                        .setAttribute(START_ANCHOR_KEY, startAnchorSource)
                        .startSpan();
        overallAppStartSpan = appStart;
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
            // check whether an activity has been created
            if (!startupTimer.uiInitStarted) {
                Log.d(RumConstants.OTEL_RUM_LOG_TAG, "Detected background app start");
                startupTimer.isStartedFromBackground = true;
            }
        }
    }

    /** Emits a one-shot AppInteractive span spanning OS process-start → now. */
    public void reportFullyDrawn() {
        if (!fullyDrawnReported.compareAndSet(false, true)) {
            return;
        }
        final Tracer t = this.tracer;
        if (t == null) {
            return;
        }
        final long endNanos = startupClock.now();
        final Span span = t.spanBuilder("AppInteractive")
                .setStartTimestamp(firstPossibleTimestamp, TimeUnit.NANOSECONDS)
                .setAttribute(START_ANCHOR_KEY, startAnchorSource)
                .startSpan();
        span.end(endNanos, TimeUnit.NANOSECONDS);
        CURRENT_INSTANCE = null;
    }
}
