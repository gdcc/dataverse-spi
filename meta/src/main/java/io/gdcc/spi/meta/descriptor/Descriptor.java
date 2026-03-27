package io.gdcc.spi.meta.descriptor;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * In-memory representation of raw metadata de/serialized from a Dataverse Plugin Metadata file.
 *
 * @param klass implementation class name
 * @param kind fully qualified base contract name
 * @param contracts map of implemented contract names to API levels. May not contain null keys or values.
 * @param requiredProviders map of required provider names to API levels. May not contain null keys or values.
 */
public record Descriptor(
    String klass,
    String kind,
    Map<String, Integer> contracts,
    Map<String, Integer> requiredProviders
) {
    
    /**
     * Creates a new plugin and defensively copies the contract/provider maps.
     *
     * <p>This ensures the plugin remains immutable even if callers pass in
     * mutable maps. All arguments must be non-null.</p>
     *
     * <p>Please note: the provided maps may not contain any null keys or values.</p>
     */
    public Descriptor {
        Objects.requireNonNull(klass);
        Objects.requireNonNull(kind);
        contracts = Map.copyOf(Objects.requireNonNull(contracts));
        requiredProviders = Map.copyOf(Objects.requireNonNull(requiredProviders));
    }
    
    /**
     * Determines whether this plugin is of a specified base contract kind.
     * Checks both {@code kind} and implemented {@code contracts}.
     *
     * @param kindFqcn the fully qualified class name of the kind to check
     * @return {@code true} if the plugin's kind matches the given class name and its contract is implemented,
     *         otherwise {@code false}
     */
    public boolean isOfKind(String kindFqcn) {
        Objects.requireNonNull(kindFqcn);
        return kind.equals(kindFqcn) && implementsContract(kindFqcn);
    }
    
    public boolean isOfKind(Class<?> kind) {
        Objects.requireNonNull(kind);
        return isOfKind(DescriptorFormat.transformClassName(kind));
    }
    
    /**
     * Checks whether this plugin declares the given implemented contract.
     *
     * @param contractFqcn the fully qualified contract class name
     * @return {@code true} if the contract is present in this plugin
     */
    public boolean implementsContract(String contractFqcn) {
        Objects.requireNonNull(contractFqcn);
        return contracts.containsKey(contractFqcn);
    }
    
    /**
     * Checks whether this plugin declares the given implemented contract.
     *
     * @param contractClass the contract class
     * @return {@code true} if the contract is present in this plugin
     */
    public boolean implementsContract(Class<?> contractClass) {
        Objects.requireNonNull(contractClass);
        return implementsContract(DescriptorFormat.transformClassName(contractClass));
    }
    
    /**
     * Returns the declared API level for the given implemented contract, if present.
     *
     * @param contractFqcn the fully qualified contract class name
     * @return the declared API level wrapped in an {@link OptionalInt}, or an empty value if absent
     */
    public int contractLevel(String contractFqcn) {
        Objects.requireNonNull(contractFqcn);
        return contracts.get(contractFqcn);
    }
    
    /**
     * Returns the declared API level for the given implemented contract, if present.
     *
     * @param contractClass the contract class
     * @return the declared API level wrapped in an {@link OptionalInt}, or an empty value if absent
     */
    public int contractLevel(Class<?> contractClass) {
        Objects.requireNonNull(contractClass);
        return contractLevel(DescriptorFormat.transformClassName(contractClass));
    }
    
    /**
     * Checks whether this plugin declares the given required provider.
     *
     * @param providerFqcn the fully qualified provider class name
     * @return {@code true} if the provider is present in this plugin
     */
    public boolean requiresProvider(String providerFqcn) {
        Objects.requireNonNull(providerFqcn);
        return requiredProviders.containsKey(providerFqcn);
    }
    
    /**
     * Checks whether this plugin declares the given required provider.
     *
     * @param providerClass the provider class
     * @return {@code true} if the provider is present in this plugin
     */
    public boolean requiresProvider(Class<?> providerClass) {
        Objects.requireNonNull(providerClass);
        return requiresProvider(DescriptorFormat.transformClassName(providerClass));
    }
    
    /**
     * Returns the declared required API level for the given provider, if present.
     *
     * @param providerFqcn the fully qualified provider class name
     * @return the required provider API level wrapped in an {@link OptionalInt}, or an empty value if absent
     */
    public int requiredProviderLevel(String providerFqcn) {
        Objects.requireNonNull(providerFqcn);
        return requiredProviders.get(providerFqcn);
    }
    
    /**
     * Returns the declared required API level for the given provider, if present.
     *
     * @param providerClass the provider class
     * @return the required provider API level wrapped in an {@link OptionalInt}, or an empty value if absent
     */
    public int requiredProviderLevel(Class<?> providerClass) {
        Objects.requireNonNull(providerClass);
        return requiredProviderLevel(DescriptorFormat.transformClassName(providerClass));
    }
}