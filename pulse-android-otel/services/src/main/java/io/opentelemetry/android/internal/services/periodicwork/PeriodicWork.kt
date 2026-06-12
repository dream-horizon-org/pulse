/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.internal.services.periodicwork

import io.opentelemetry.android.internal.services.Service

interface PeriodicWork : Service {
    fun enqueue(runnable: Runnable)
}
