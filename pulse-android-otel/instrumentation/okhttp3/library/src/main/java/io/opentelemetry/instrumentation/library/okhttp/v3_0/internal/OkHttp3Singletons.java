/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.okhttp.v3_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientRequestResendCount;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.library.okhttp.v3_0.OkHttpInstrumentation;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.ConnectionErrorSpanInterceptor;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.OkHttpAttributesGetter;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.OkHttpClientInstrumenterBuilderFactory;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.TracingInterceptor;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OkHttp3Singletons {
    private static final Interceptor NOOP_INTERCEPTOR = chain -> chain.proceed(chain.request());
    private static final String RN_TRACKED_HEADER = "x-pulse-rn-tracked";

    /**
     * Delegates through [AtomicReference] so existing [OkHttpClient] instances that already
     * registered [TRACING_INTERCEPTOR] / [CONNECTION_ERROR_INTERCEPTOR] stop calling the real
     * instrumented interceptors after [disableInstrumentedInterceptors] (e.g. Pulse SDK shutdown),
     * without rebuilding the client.
     */
    private static final AtomicReference<Interceptor> tracingHead =
            new AtomicReference<>(NOOP_INTERCEPTOR);

    private static final AtomicReference<Interceptor> connectionErrorHead =
            new AtomicReference<>(NOOP_INTERCEPTOR);

    public static final Interceptor TRACING_INTERCEPTOR =
            chain -> tracingHead.get().intercept(chain);

    public static final Interceptor CONNECTION_ERROR_INTERCEPTOR =
            chain -> connectionErrorHead.get().intercept(chain);

    public static void configure(
            OkHttpInstrumentation instrumentation, OpenTelemetry openTelemetry) {
        DefaultHttpClientInstrumenterBuilder<Interceptor.Chain, Response> instrumenterBuilder =
                OkHttpClientInstrumenterBuilderFactory.create(openTelemetry)
                        .setCapturedRequestHeaders(instrumentation.getCapturedRequestHeaders())
                        .setCapturedResponseHeaders(instrumentation.getCapturedResponseHeaders())
                        .setKnownMethods(instrumentation.getKnownMethods())
                        // TODO: Do we really need to set the known methods on the span
                        // name
                        // extractor as well?
                        .setSpanNameExtractor(
                                x ->
                                        HttpSpanNameExtractor.builder(
                                                        OkHttpAttributesGetter.INSTANCE)
                                                .setKnownMethods(instrumentation.getKnownMethods())
                                                .build())
                        .addAttributesExtractor(
                                PeerServiceAttributesExtractor.create(
                                        OkHttpAttributesGetter.INSTANCE,
                                        instrumentation.newPeerServiceResolver()))
                        .setEmitExperimentalHttpClientTelemetry(
                                instrumentation.emitExperimentalHttpClientTelemetry());

        for (AttributesExtractor<Interceptor.Chain, Response> extractor :
                instrumentation.additionalExtractors) {
            instrumenterBuilder = instrumenterBuilder.addAttributesExtractor(extractor);
        }

        Instrumenter<Interceptor.Chain, Response> instrumenter = instrumenterBuilder.build();

        ConnectionErrorSpanInterceptor baseConnectionErrorInterceptor =
                new ConnectionErrorSpanInterceptor(instrumenter);
        TracingInterceptor baseTracingInterceptor =
                new TracingInterceptor(instrumenter, openTelemetry.getPropagators());

        connectionErrorHead.set(
                skipInstrumentationIfReactNativeTracked(baseConnectionErrorInterceptor));
        tracingHead.set(skipInstrumentationIfReactNativeTracked(baseTracingInterceptor));
    }

    /**
     * Stops OkHttp span creation after the SDK/tracer provider is shut down. Safe to call multiple
     * times.
     */
    public static void disableInstrumentedInterceptors() {
        connectionErrorHead.set(NOOP_INTERCEPTOR);
        tracingHead.set(NOOP_INTERCEPTOR);
    }

    public static final Interceptor CALLBACK_CONTEXT_INTERCEPTOR =
            chain -> {
                Request request = chain.request();
                Context context =
                        OkHttpCallbackAdviceHelper.tryRecoverPropagatedContextFromCallback(request);
                if (context != null) {
                    try (Scope ignored = context.makeCurrent()) {
                        return chain.proceed(request);
                    }
                }

                return chain.proceed(request);
            };

    public static final Interceptor RESEND_COUNT_CONTEXT_INTERCEPTOR =
            chain -> {
                try (Scope ignored =
                        HttpClientRequestResendCount.initialize(Context.current()).makeCurrent()) {
                    return chain.proceed(chain.request());
                }
            };

    private static Interceptor skipInstrumentationIfReactNativeTracked(
            Interceptor baseInterceptor) {
        return chain -> {
            Request request = chain.request();
            if (request.header(RN_TRACKED_HEADER) != null) {
                return chain.proceed(request);
            }
            return baseInterceptor.intercept(chain);
        };
    }

    private OkHttp3Singletons() {}
}
