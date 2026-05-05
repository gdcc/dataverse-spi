/**
 * Provides descriptor models and utilities for plugin metadata discovery, serialization, and runtime interpretation.
 *
 * <p>This package contains:
 * <ul>
 *   <li>immutable descriptor types representing plugin metadata,</li>
 *   <li>format utilities for reading and writing descriptor resources, and</li>
 *   <li>scanning support for locating descriptor definitions in directories and JAR files.</li>
 * </ul>
 *
 * <p>The package separates metadata concerns into distinct layers:
 * <ul>
 *   <li>raw descriptor data represented in a serialized or transport-friendly form,</li>
 *   <li>source-aware descriptor views that retain origin information, and</li>
 *   <li>runtime-facing descriptors that use resolved Java types.</li>
 * </ul>
 *
 * <p>All descriptor value types are designed to be immutable and safe to share.
 * Utility classes in this package provide stateless helper methods for working
 * with descriptor files and service metadata.
 *
 * <p>Unless otherwise noted, {@code null} values are not permitted.
 */
package io.gdcc.spi.meta.descriptor;