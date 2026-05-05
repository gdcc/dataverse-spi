/**
 * Defines the core SPI contracts for plugins and core-provided services.
 *
 * <p>This package contains the foundational marker and contract interfaces used by the Dataverse Plugin System:
 * <ul>
 *   <li>{@link io.gdcc.spi.meta.plugin.Plugin}, the supertype base contract for any Dataverse Plugin contracts, and</li>
 *   <li>{@link io.gdcc.spi.meta.plugin.CoreProvider}, the supertype base contract for framework-provided services that plugins may depend on.</li>
 * </ul>
 *
 * <p>These types form the most basic public interaction layer between the Dataverse core and community contributed
 * plugin implementations. Plugin contracts are intended to be stable, minimal, and easy to implement.
 *
 * <p>Contracts and plugin implementations are expected to provide clear, machine-readable identities and to participate in the wider
 * plugin runtime through metadata and discovery mechanisms, defined by annotations from {@link io.gdcc.spi.meta.annotations}.
 *
 * <p>Unless otherwise noted, {@code null} values are not permitted.
 */
package io.gdcc.spi.meta.plugin;