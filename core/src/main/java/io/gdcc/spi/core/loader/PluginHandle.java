package io.gdcc.spi.core.loader;

import io.gdcc.spi.meta.descriptor.PluginDescriptor;
import io.gdcc.spi.meta.plugin.Plugin;

import java.util.Objects;

/**
 * Encapsulates a plugin and its corresponding descriptor, providing a unified representation
 * of a resolved plugin and its metadata in the runtime context.
 *
 * <p>The {@code PluginHandle} is an immutable record that binds a concrete plugin instance
 * with its associated {@link PluginDescriptor}. This ensures both the metadata and the
 * operational plugin instance are accessible and linked together, facilitating plugin management
 * and execution.</p>
 *
 * @param <T> the type of the plugin instance, which must extend {@link Plugin}
 * @param descriptor the runtime descriptor containing metadata and implementation details
 *                   about the plugin; must not be null
 * @param plugin the actual plugin instance associated with the descriptor; must not be null
 */
public record PluginHandle<T extends Plugin>(
    PluginDescriptor<T> descriptor,
    T plugin
) {
    public PluginHandle {
        Objects.requireNonNull(descriptor, "Plugin descriptor cannot be null");
        Objects.requireNonNull(plugin, "Plugin instance cannot be null");
    }
}
