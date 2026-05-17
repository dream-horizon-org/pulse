/**
 * Pure error-grouping heuristics for Pulse.
 *
 * <p>Everything in this module must be deterministic and side-effect-free:
 * no Guice injection, no database access, no file I/O, no network calls.
 * It exists to keep the bucketing logic testable and evolvable in isolation
 * from the OTLP ingestion pipeline that consumes it.</p>
 */
package org.dreamhorizon.pulseserver.grouping;
