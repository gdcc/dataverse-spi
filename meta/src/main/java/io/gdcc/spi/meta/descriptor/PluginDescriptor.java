package io.gdcc.spi.meta.descriptor;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * In-memory representation of one generated plugin descriptor.
 *
 * @param pluginClass implementation class name
 * @param pluginKind fully qualified base contract name
 * @param contracts map of implemented contract names to API levels
 * @param requiredProviders map of required provider names to API levels
 */
public record PluginDescriptor(
    String pluginClass,
    String pluginKind,
    Map<String, Integer> contracts,
    Map<String, Integer> requiredProviders
) {
    
    /**
     * Creates a new descriptor and defensively copies the contract/provider maps.
     *
     * <p>This ensures the descriptor remains immutable even if callers pass in
     * mutable maps. All arguments must be non-null.</p>
     */
    public PluginDescriptor {
        Objects.requireNonNull(pluginClass);
        Objects.requireNonNull(pluginKind);
        contracts = Map.copyOf(Objects.requireNonNull(contracts));
        requiredProviders = Map.copyOf(Objects.requireNonNull(requiredProviders));
    }
    
    /**
     * Checks whether this plugin declares the given implemented contract.
     *
     * @param contractFqcn the fully qualified contract class name
     * @return {@code true} if the contract is present in this descriptor
     */
    public boolean implementsContract(String contractFqcn) {
        Objects.requireNonNull(contractFqcn);
        return contracts.containsKey(contractFqcn);
    }
    
    /**
     * Checks whether this plugin declares the given implemented contract.
     *
     * @param contractClass the contract class
     * @return {@code true} if the contract is present in this descriptor
     */
    public boolean implementsContract(Class<?> contractClass) {
        Objects.requireNonNull(contractClass);
        return implementsContract(contractClass.getCanonicalName());
    }
    
    /**
     * Returns the declared API level for the given implemented contract, if present.
     *
     * @param contractFqcn the fully qualified contract class name
     * @return the declared API level wrapped in an {@link OptionalInt}, or an empty value if absent
     */
    public OptionalInt contractLevel(String contractFqcn) {
        Objects.requireNonNull(contractFqcn);
        Integer value = contracts.get(contractFqcn);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }
    
    /**
     * Returns the declared API level for the given implemented contract, if present.
     *
     * @param contractClass the contract class
     * @return the declared API level wrapped in an {@link OptionalInt}, or an empty value if absent
     */
    public OptionalInt contractLevel(Class<?> contractClass) {
        Objects.requireNonNull(contractClass);
        return contractLevel(contractClass.getCanonicalName());
    }
    
    /**
     * Returns the declared API level for the given implemented contract.
     *
     * @param contractFqcn the fully qualified contract class name
     * @return the declared API level
     * @throws IllegalArgumentException if the contract is not present in this descriptor
     */
    public int contractLevelOrThrow(String contractFqcn) {
        return contractLevel(contractFqcn)
            .orElseThrow(() -> new IllegalArgumentException("Unknown contract " + contractFqcn));
    }
    
    /**
     * Returns the declared API level for the given implemented contract.
     *
     * @param contractClass the contract class
     * @return the declared API level
     * @throws IllegalArgumentException if the contract is not present in this descriptor
     */
    public int contractLevelOrThrow(Class<?> contractClass) {
        Objects.requireNonNull(contractClass);
        return contractLevelOrThrow(contractClass.getCanonicalName());
    }
    
    /**
     * Checks whether this plugin declares the given required provider.
     *
     * @param providerFqcn the fully qualified provider class name
     * @return {@code true} if the provider is present in this descriptor
     */
    public boolean requiresProvider(String providerFqcn) {
        Objects.requireNonNull(providerFqcn);
        return requiredProviders.containsKey(providerFqcn);
    }
    
    /**
     * Checks whether this plugin declares the given required provider.
     *
     * @param providerClass the provider class
     * @return {@code true} if the provider is present in this descriptor
     */
    public boolean requiresProvider(Class<?> providerClass) {
        Objects.requireNonNull(providerClass);
        return requiresProvider(providerClass.getCanonicalName());
    }
    
    /**
     * Returns the declared required API level for the given provider, if present.
     *
     * @param providerFqcn the fully qualified provider class name
     * @return the required provider API level wrapped in an {@link OptionalInt}, or an empty value if absent
     */
    public OptionalInt requiredProviderLevel(String providerFqcn) {
        Objects.requireNonNull(providerFqcn);
        Integer value = requiredProviders.get(providerFqcn);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }
    
    /**
     * Returns the declared required API level for the given provider, if present.
     *
     * @param providerClass the provider class
     * @return the required provider API level wrapped in an {@link OptionalInt}, or an empty value if absent
     */
    public OptionalInt requiredProviderLevel(Class<?> providerClass) {
        Objects.requireNonNull(providerClass);
        return requiredProviderLevel(providerClass.getCanonicalName());
    }
    
    /**
     * Returns the declared required API level for the given provider.
     *
     * @param providerFqcn the fully qualified provider class name
     * @return the required provider API level
     * @throws IllegalArgumentException if the provider is not present in this descriptor
     */
    public int requiredProviderLevelOrThrow(String providerFqcn) {
        return requiredProviderLevel(providerFqcn)
            .orElseThrow(() -> new IllegalArgumentException("Unknown required provider " + providerFqcn));
    }
    
    /**
     * Returns the declared required API level for the given provider.
     *
     * @param providerClass the provider class
     * @return the required provider API level
     * @throws IllegalArgumentException if the provider is not present in this descriptor
     */
    public int requiredProviderLevelOrThrow(Class<?> providerClass) {
        Objects.requireNonNull(providerClass);
        return requiredProviderLevelOrThrow(providerClass.getCanonicalName());
    }
}