package io.gdcc.spi.meta.descriptor;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A record representing a descriptor that is sourced from a specific location.
 * Combines information about a descriptor and its source location.
 *
 * @param sourceLocation the path to the source location of the descriptor, must not be null
 * @param plugin the {@link Descriptor} representing the plugin information, must not be null
 */
public record SourcedDescriptor(Path sourceLocation, Descriptor plugin) {
    
    public SourcedDescriptor {
        Objects.requireNonNull(sourceLocation);
        Objects.requireNonNull(plugin);
    }
    
    public boolean isOfKind(Class<?> contractClass) {
        return plugin.isOfKind(contractClass);
    }
}
