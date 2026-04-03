package io.gdcc.spi.meta.descriptor;

import io.gdcc.spi.meta.plugin.CoreProvider;
import io.gdcc.spi.meta.plugin.Plugin;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Runtime-facing descriptor of a resolved and loaded plugin implementation.
 *
 * <p>This descriptor represents a plugin after metadata has been interpreted in the context of the
 * running application and the relevant Java types have been resolved. Unlike the build-time or
 * serialized descriptor form, this model uses actual {@link Class} references for the plugin
 * implementation, its base contract, all implemented plugin contracts, and all required core
 * providers.</p>
 *
 * <p>The generic type parameter {@code T} represents the base plugin contract under which the plugin
 * was resolved and loaded. The {@code pluginClass} is therefore guaranteed to implement that base
 * contract, while {@code kindClass} denotes the concrete base plugin contract itself.</p>
 *
 * <p>The {@code contracts} map contains all plugin contracts implemented by the plugin together with
 * their declared API levels. This includes the base contract as well as any optional capability
 * contracts. The {@code requiredProviders} map contains all core providers required by the plugin's
 * implemented contracts, again paired with their declared API levels.</p>
 *
 * @param <T> the base plugin contract type under which this plugin was resolved
 * @param sourceLocation the source location from which the plugin was loaded, such as a JAR file or
 *                       exploded classpath directory
 * @param identity the logical plugin identity reported by the plugin instance; intended for
 *                 distinguishing plugins at runtime
 * @param pluginClass the concrete implementation class of the plugin
 * @param kindClass the resolved base plugin contract implemented by the plugin
 * @param contracts all resolved plugin contracts implemented by the plugin, mapped to their
 *                  declared API levels
 * @param requiredProviders all resolved core providers required by the plugin, mapped to their
 *                          required API levels
 */
public record PluginDescriptor<T extends Plugin>(
    Path sourceLocation,
    String identity,
    Class<? extends T> pluginClass,
    Class<T> kindClass,
    Map<Class<? extends Plugin>, Integer> contracts,
    Map<Class<? extends CoreProvider>, Integer> requiredProviders
) {
    
    public PluginDescriptor {
        Objects.requireNonNull(sourceLocation);
        Objects.requireNonNull(identity);
        Objects.requireNonNull(pluginClass);
        Objects.requireNonNull(kindClass);
        Objects.requireNonNull(contracts);
        Objects.requireNonNull(requiredProviders);
        
        // Immutability is key
        contracts = Map.copyOf(contracts);
        requiredProviders = Map.copyOf(requiredProviders);
        
        // Sane structure checks
        if (identity.isBlank())
            throw new IllegalArgumentException("Plugin identity cannot be blank");
        if (contracts.isEmpty())
            throw new IllegalArgumentException("Plugin must implement at least one contract (the kindClass one)");
    }
    
    public boolean implementsContract(Class<? extends Plugin> contractClass) {
        return this.contracts.containsKey(contractClass);
    }
    
    public OptionalInt contractLevel(Class<? extends Plugin> contractClass) {
        return implementsContract(contractClass)
            ? OptionalInt.of(this.contracts.get(contractClass))
            : OptionalInt.empty();
    }
    
    public boolean requiresProvider(Class<? extends CoreProvider> providerClass) {
        return this.requiredProviders.containsKey(providerClass);
    }
    
    public OptionalInt requiredProviderLevel(Class<? extends CoreProvider> providerClass) {
        return requiresProvider(providerClass)
            ? OptionalInt.of(this.requiredProviders.get(providerClass))
            : OptionalInt.empty();
    }
    
    /**
     * Returns the normalized identity string of this plugin.
     * The normalization process converts the identity to lowercase
     * and removes special characters such as "/\\-_:.#~*", ensuring
     * a consistent format for comparison purposes.
     *
     * @return the normalized identity string, or null if the original identity is null
     */
    public String normalizedIdentity() {
        return normalizeIdentity(this.identity);
    }
    
    /**
     * Normalizes the given identity string for comparison purposes by converting it to lowercase
     * and removing all occurrences of the characters "/\-_:.#~*", which are commonly used to separate words.
     * This avoids having multiple plugins targeting the same thing, like an export format with a slightly different
     * case or special characters.
     *
     * @param identity the identity string to normalize
     * @return the normalized identity string, or null if the input is null
     */
    private String normalizeIdentity(String identity) {
        if (identity == null) return null;
        return identity.toLowerCase().replaceAll("[/\\\\_\\-:.#~*]+", "");
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        PluginDescriptor<?> that = (PluginDescriptor<?>) obj;
        
        return Objects.equals(sourceLocation, that.sourceLocation) &&
            Objects.equals(pluginClass, that.pluginClass) &&
            (
                Objects.equals(identity, that.identity) ||
                Objects.equals(normalizeIdentity(identity), normalizeIdentity(that.identity))
            );
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sourceLocation, pluginClass, normalizeIdentity(identity));
    }
}
