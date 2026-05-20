/**
 * One short utility class per heuristic phase, all pure functions that mutate
 * (or read) the shared {@link org.dreamhorizon.pulseserver.grouping.model.ParsedFrames}
 * container. Composition lives in
 * {@link org.dreamhorizon.pulseserver.grouping.Grouper}.
 *
 * <p>No I/O, no Guice, no DB — the module-wide invariant from
 * {@link org.dreamhorizon.pulseserver.grouping} applies here too.</p>
 */
package org.dreamhorizon.pulseserver.grouping.phase;
