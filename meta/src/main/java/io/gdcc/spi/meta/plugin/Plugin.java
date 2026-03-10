package io.gdcc.spi.meta.plugin;

/**
 * Represents the contract for plugins in the system. Implementations of this interface serve
 * as modular components that can be dynamically loaded and integrated into the broader application.
 *
 * Each plugin must provide a unique, machine-readable identifier to ensure proper identification
 * and usage within the system.
 *
 * Implementers are required to define the {@link #identity()} method to specify their unique
 * identifier.
 *
 * @see CoreProvider
 */
public interface Plugin {
    
    /**
     * Returns the unique, machine-readable identifier for this plugin.
     * This will be the primary key within the core to identify a specific plugin implementation.
     *
     * @return the plugin's identity string, which must be non-null, non-blank, and URL compatible.
     * @implSpec This method must be overridden by any plugin implementation and return a non-null, non-blank,
     *           URL-compatible string. No plugin interface may provide a default implementation.
     */
    String identity();
}
