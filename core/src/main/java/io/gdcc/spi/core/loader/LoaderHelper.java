package io.gdcc.spi.core.loader;

import io.gdcc.spi.meta.descriptor.DescriptorFormat;
import io.gdcc.spi.meta.descriptor.DescriptorScanner;
import io.gdcc.spi.meta.descriptor.PluginDescriptor;
import io.gdcc.spi.meta.descriptor.SourcedDescriptor;
import io.gdcc.spi.meta.plugin.CoreProvider;
import io.gdcc.spi.meta.plugin.Plugin;
import io.gdcc.spi.meta.processor.ProcessorConstants;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides utility methods for handling plugin loading and validation operations,
 * such as converting file paths to URLs, checking class presence, determining core API levels,
 * and validating plugin descriptors. It's intended for internal use only.
 */
final class LoaderHelper {
    
    private LoaderHelper() {
        // Intentionally left blank for helper singleton
    }
    
    /**
     * Converts a {@link Path} object to a {@link URL} with optional prefix and suffix.
     *
     * @param path the {@link Path} to convert to a {@link URL}; must not be null
     * @param urlPrefix an optional String to prepend to the URL (e.g., a scheme); can be null or blank
     * @param urlSuffix an optional String to append to the URL; can be null or blank
     * @return the constructed {@link URL} based on the provided path, prefix, and suffix
     * @throws MalformedURLException if the constructed URL is invalid
     */
    static URL pathToUrl(Path path, String urlPrefix, String urlSuffix) throws MalformedURLException {
        return new URL(
            (urlPrefix == null || urlPrefix.isBlank() ? "" : urlPrefix + ":" ) +
                path.toUri().toURL() +
                ( urlSuffix == null || urlSuffix.isBlank() ? "" : urlSuffix )
        );
    }
    
    /**
     * Checks if a class with the specified fully qualified class name (FQCN) is present and
     * accessible using the given {@link ClassLoader}.
     *
     * @param fqcn         the fully qualified name of the class to be checked
     * @param classLoader  the {@link ClassLoader} to use for detecting the class
     * @return true if the class is present and accessible; false otherwise
     */
    static boolean isClassPresent(String fqcn, ClassLoader classLoader) {
        try {
            Class.forName(fqcn, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
    
    
    /**
     * Determines the core API level of the given plugin class. This method checks for a static field
     * named "API_LEVEL" in the specified class and returns its integer value if present and accessible.
     *
     * @param pluginClass the plugin class to check for the API level. The class must be an interface.
     * @return an {@code OptionalInt} containing the API level if the field exists and is accessible;
     *         {@code OptionalInt.empty()} otherwise.
     * @throws IllegalArgumentException if the provided class is not an interface.
     */
    static int determineCoreApiLevel(Class<?> pluginClass) {
        // Looking up the plugin contract API level is only ever valid on SPI interfaces but never on implementations.
        if (!pluginClass.isInterface()) {
            throw new IllegalArgumentException("Class must be an interface");
        }
        try {
            // Retrieve the field from exactly this class (we don't want to search any superclasses here!)
            Field apiLevel = pluginClass.getDeclaredField(ProcessorConstants.API_LEVEL_FIELD_NAME);
            return apiLevel.getInt(pluginClass);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Contract class must have an (accessible) " + ProcessorConstants.API_LEVEL_FIELD_NAME + " field");
        }
    }
    
    /**
     * Determines the core API level of a given plugin class by its fully qualified class name.
     *
     * @param className the fully qualified name of the class to evaluate
     * @return the core API level of the specified plugin class
     * @throws IllegalArgumentException if the specified class cannot be found
     */
    static int determineCoreApiLevel(String className, ClassLoader classLoader) {
        Class<?> pluginClass = resolveClass(className, classLoader);
        return determineCoreApiLevel(pluginClass);
    }
    
    /**
     * Resolves and returns the {@link Class} object for the specified fully qualified class name
     * using the provided {@link ClassLoader}.
     *
     * @param className the fully qualified name of the class to resolve; must not be null or empty
     * @param classLoader the {@link ClassLoader} to use for loading the class; must not be null
     * @return the {@link Class} object representing the loaded class
     * @throws IllegalArgumentException if the class cannot be found or loaded
     */
    static Class<?> resolveClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class " + className + " could not be found in core", e);
        }
    }
    
    /**
     * Validates a list of plugin descriptors to ensure there are no class name collisions either between
     * plugins or with the core Java classpath. The method identifies plugins with conflicting class names
     * and records the issues for further analysis.
     *
     * @param descriptors a list of {@code SourcedDescriptor} objects representing the plugins to validate.
     *        Each descriptor contains information about the plugin's source location and associated class.
     * @param classLoader the {@code ClassLoader} used to check for class name conflicts with the core system.
     * @return a {@code PluginValidationResult<SourcedDescriptor>} object that contains two categories:
     *         - {@code accepted}: A list of plugins that passed validation without conflicts.
     *         - {@code rejected}: A map of rejected plugins to a list of {@code LoaderProblem} objects detailing
     *           the specific reasons for rejection.
     */
    static PluginValidationResult<SourcedDescriptor> verifyNoClassCollisions(List<SourcedDescriptor> descriptors, ClassLoader classLoader) {
        // Scratch spaces to build the result
        Set<SourcedDescriptor> accepted = new HashSet<>();
        Map<SourcedDescriptor, List<LoaderProblem>> rejected = new HashMap<>();
        
        // Just an ephemeral scratch space to save what we've already seen as plugins from where
        Map<String, Path> classToSourcePath = new HashMap<>();
        
        // Iterate through all the discovered plugins
        descriptors.forEach(descriptor -> {
            
            String className = descriptor.plugin().klass();
            Path source = descriptor.sourceLocation();
            List<LoaderProblem> problems = new ArrayList<>();
            
            // Check if the classname was already provided from a different location
            if (classToSourcePath.containsKey(className)) {
                problems.add(new LoaderProblem.PluginClassNameCollision(className, classToSourcePath.get(className), source));
            } else {
                classToSourcePath.put(className, source);
            }
            
            // Check if the classname is already present on the current class path (the core)
            if (isClassPresent(className, classLoader)) {
                problems.add(new LoaderProblem.PluginClassNameCollisionWithCore(className, source));
            }
            
            // Let the record show...
            if (problems.isEmpty()) {
                accepted.add(descriptor);
            } else {
                rejected.put(descriptor, problems);
            }
        });
        
        return new PluginValidationResult<>(Set.copyOf(accepted), PluginValidationResult.copyProblemMap(rejected), Map.of());
    }
    
    
    /**
     * Identifies and classifies plugin descriptors that are not implementations of the specified plugin
     * contract class. The method evaluates each descriptor to determine whether its associated plugin
     * adheres to the given plugin contract. Plugins are then categorized as accepted, rejected, or
     * warning-based, depending on their compatibility and the loader configuration.
     *
     * @param descriptors a list of {@code SourcedDescriptor} objects representing the plugins to be evaluated.
     *        Each descriptor contains information about the plugin's source location and associated class.
     * @param pluginClass the {@code Class} object representing the plugin contract that the plugins
     *        should implement.
     * @param configuration a {@code LoaderConfiguration} object that dictates specific validation behaviors
     *        and enforcement rules when classifying the plugins.
     * @return a {@code PluginValidationResult<SourcedDescriptor>} object containing:
     *         - {@code accepted}: A set of plugins that fully adhere to the specified plugin contract.
     *         - {@code warning}: A map of plugins with potential issues or warnings that do not warrant rejection.
     *         - {@code rejected}: A map of plugins that were rejected due to failing to meet the plugin contract
     *           or violating enforced loader rules.
     */
    static PluginValidationResult<SourcedDescriptor> identifyNonImplementations(
            List<SourcedDescriptor> descriptors, Class<?> pluginClass, LoaderConfiguration configuration) {
        // Scratch spaces to build the result
        Set<SourcedDescriptor> accepted = new HashSet<>();
        Map<SourcedDescriptor, List<LoaderProblem>> warning = new HashMap<>();
        Map<SourcedDescriptor, List<LoaderProblem>> rejected = new HashMap<>();
        
        // Check if the plugin to be loaded is an implementation of the plugin contract the loader was signed up for
        descriptors.forEach(descriptor -> {
            List<LoaderProblem> problems = new ArrayList<>();
            
            if (!descriptor.isOfKind(pluginClass)) {
                problems.add(new LoaderProblem.PluginClassMismatch(
                    descriptor.plugin().klass(),
                    descriptor.sourceLocation(),
                    pluginClass.getCanonicalName()
                ));
                
                if (configuration.ENFORCE_SINGLE_SOURCE_MATCHING_PLUGINS_ONLY())
                    rejected.put(descriptor, problems);
                else {
                    warning.put(descriptor, problems);
                }
            } else {
                accepted.add(descriptor);
            }
        });
        
        return new PluginValidationResult<>(
            Set.copyOf(accepted),
            PluginValidationResult.copyProblemMap(rejected),
            PluginValidationResult.copyProblemMap(warning)
        );
    }
    
    
    /**
     * Verifies service provider records in the provided list of descriptors.
     * This method examines each descriptor to determine if it contains a valid
     * service provider interface (SPI) record. If a descriptor contains an SPI record,
     * it is accepted; otherwise, it is rejected with a corresponding list of problems.
     * The results of the verification process are returned as a {@code PluginValidationResult}.
     *
     * @param descriptors a list of {@code SourcedDescriptor} objects to be validated
     * @return a {@code PluginValidationResult} containing accepted descriptors and associated rejection details
     */
    static PluginValidationResult<SourcedDescriptor> verifyServiceProviderRecords(List<SourcedDescriptor> descriptors) {
        // Scratch spaces to build the result
        Set<SourcedDescriptor> accepted = new HashSet<>();
        Map<SourcedDescriptor, List<LoaderProblem>> rejected = new HashMap<>();
        
        for (SourcedDescriptor descriptor : descriptors) {
            try {
                if (DescriptorScanner.hasServiceProviderInterfaceRecord(descriptor)) {
                    accepted.add(descriptor);
                } else {
                    rejected.put(
                        descriptor,
                        List.of(new LoaderProblem.MissingServiceProviderRecord(
                            descriptor.plugin().klass(),
                            descriptor.plugin().kind(),
                            descriptor.sourceLocation())
                    ));
                }
            } catch (IOException | IllegalArgumentException e) {
                rejected.put(descriptor, List.of(new LoaderProblem.LocationFailure(descriptor.sourceLocation(), e)));
            }
        }
        
        return new PluginValidationResult<>(
            Set.copyOf(accepted),
            PluginValidationResult.copyProblemMap(rejected),
            Map.of()
        );
    }
    
    
    /**
     * Validates a list of plugin descriptors to ensure their API levels are compatible with the specified
     * plugin contract class. The method verifies that each plugin both declares and adheres to the required
     * API level for its declared contracts. Any discrepancies, such as missing contracts, mismatched API levels,
     * or unsupported contracts, are recorded as validation problems.
     *
     * @param descriptors a list of {@code SourcedDescriptor} objects representing the plugins to validate.
     *        Each descriptor contains information about the plugin's source location and associated plugin details,
     *        including the contracts and API levels it supports.
     * @param pluginClass the {@code Class} object representing the plugin contract that the plugins must
     *        comply with. The desired API level for this contract will be determined and used as part of
     *        the validation process.
     * @return a {@code PluginValidationResult<SourcedDescriptor>} object containing:
     *         - {@code accepted}: A set of plugins that fully adhere to the API level requirements for the
     *           specified plugin contract.
     *         - {@code rejected}: A map of plugins that failed validation, paired with a list of
     *           {@code LoaderProblem} objects describing the specific issues encountered.
     */
    static PluginValidationResult<SourcedDescriptor> verifyPluginApiLevels(List<SourcedDescriptor> descriptors, Class<?> pluginClass, ClassLoader classLoader) {
        // Scratch spaces to build the result
        Set<SourcedDescriptor> accepted = new HashSet<>();
        Map<SourcedDescriptor, List<LoaderProblem>> rejected = new HashMap<>();
        
        // Determine the plugin contract's name once as a string for comparisons
        String desiredPluginClass = DescriptorFormat.transformClassName(pluginClass);
        int desiredPluginApiLevel = determineCoreApiLevel(pluginClass);
        
        // Iterate over all the plugins
        for (SourcedDescriptor descriptor : descriptors) {
            // Need to track if the base contract appears in the contracts including a level
            boolean hasBaseClassContract = false;
            // Save all the problems identified during validation
            List<LoaderProblem> problems = new ArrayList<>();
            
            String pluginImplementationClass = descriptor.plugin().klass();
            
            // Iterate all the implemented capabilities and check their levels
            for (Map.Entry<String, Integer> contract : descriptor.plugin().contracts().entrySet()) {
                String pluginContractClass = contract.getKey();
                int pluginContractApiLevel = contract.getValue();
                
                // Let the record show that this plugin does claim to implement the base contract for this loader
                if (desiredPluginClass.equals(pluginContractClass)) {
                    hasBaseClassContract = true;
                }
                
                try {
                    // Extract the API level the core is expecting for this contract
                    int coreContractApiLevel = determineCoreApiLevel(pluginContractClass, classLoader);
                    
                    // Compare base class core levels: they must match exactly, otherwise record a problem
                    if (coreContractApiLevel != pluginContractApiLevel) {
                        problems.add(new LoaderProblem.PluginClassApiLevelMismatch(
                            pluginImplementationClass,
                            descriptor.sourceLocation(),
                            coreContractApiLevel,
                            pluginContractApiLevel)
                        );
                    }
                } catch (IllegalArgumentException e) {
                    problems.add(new LoaderProblem.PluginClassUnsupported(
                        pluginImplementationClass,
                        descriptor.sourceLocation(),
                        pluginContractClass)
                    );
                }
            }
            
            // If the plugin did not provide a level for the base contract this loader expects, this is a serious problem...
            if (!hasBaseClassContract) {
                problems.add(new LoaderProblem.PluginClassApiLevelMissing(
                    pluginImplementationClass,
                    descriptor.sourceLocation(),
                    desiredPluginClass,
                    desiredPluginApiLevel)
                );
            }
            
            if (problems.isEmpty()) {
                accepted.add(descriptor);
            } else {
                rejected.put(descriptor, List.copyOf(problems));
            }
        }
        
        return new PluginValidationResult<>(
            Set.copyOf(accepted),
            PluginValidationResult.copyProblemMap(rejected),
            Map.of()
        );
    }
    
    /**
     * Converts a {@link SourcedDescriptor} and a plugin instance into a {@link PluginDescriptor}.
     *
     * @param <T> The type of the plugin, constrained to extend {@link Plugin}.
     * @param sourceDescriptor The descriptor providing metadata about the source and configuration of the plugin.
     *                          Must not be null.
     * @param plugin The actual plugin instance to be described. Must not be null and must have a valid identity.
     * @param classLoader The {@link ClassLoader} used to resolve any required classes. Must not be null.
     * @return A {@link PluginDescriptor} object that encapsulates the metadata, identity, and other properties
     *         of the provided plugin.
     * @throws NullPointerException If any of the input parameters is null.
     * @throws IllegalArgumentException If the plugin's identity is null or blank, or any classes cannot be resolved.
     */
    static <T extends Plugin> PluginDescriptor<T> toPluginDescriptor(
        SourcedDescriptor sourceDescriptor,
        T plugin,
        ClassLoader classLoader
    ) {
        Objects.requireNonNull(sourceDescriptor);
        Objects.requireNonNull(plugin);
        Objects.requireNonNull(classLoader);
        
        String identity = plugin.identity();
        if (identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("Plugin identity may not be null or blank");
        }
        
        // The cast is necessary as getClass() returns T, but the actual implementation class is required
        // At this point, we know that "plugin" is an instance of an implementation, so this operation is safe.
        // T is an interface, as checked by the plugin loader during construction.
        @SuppressWarnings("unchecked")
        Class<? extends T> pluginClass = (Class<? extends T>) plugin.getClass();
        
        // When reaching this point, the metadata verification already made sure that the plugin kind equals T.
        // Casting here is safe.
        @SuppressWarnings("unchecked")
        Class<T> kindClass = (Class<T>) resolveClass(sourceDescriptor.plugin().kind(), classLoader);
        
        Map<Class<? extends Plugin>, Integer> contracts = new HashMap<>();
        sourceDescriptor.plugin().contracts().forEach((contractName, apiLevel) -> {
            // Again, the metadata was already vetted to contain valid contract classes. Casting is safe here.
            @SuppressWarnings("unchecked")
            Class<? extends Plugin> contractClass = (Class<? extends Plugin>) resolveClass(contractName, classLoader);
            contracts.put(contractClass, apiLevel);
        });
        
        Map<Class<? extends CoreProvider>, Integer> requiredProviders = new HashMap<>();
        sourceDescriptor.plugin().requiredProviders().forEach((providerName, apiLevel) -> {
            // Again, the metadata was already vetted to contain valid provider requirements. Casting is safe here.
            @SuppressWarnings("unchecked")
            Class<? extends CoreProvider> providerClass = (Class<? extends CoreProvider>) resolveClass(providerName, classLoader);
            requiredProviders.put(providerClass, apiLevel);
        });
        
        return new PluginDescriptor<>(
            sourceDescriptor.sourceLocation(),
            identity,
            pluginClass,
            kindClass,
            contracts,
            requiredProviders
        );
    }
    
    /**
     * Verifies the uniqueness of plugin identities within the provided set of plugins.
     * Identifies duplicates based on the normalized identity of each plugin descriptor
     * and returns a validation result categorizing acceptable and problematic plugins.
     *
     * If the provided configuration enforces unambiguous identities, duplicates will
     * be treated as rejected. Otherwise, duplicates will be reported as warnings.
     *
     * @param <T> The type of the plugin.
     * @param plugins The set of plugin descriptors to validate.
     * @param configuration The loader configuration that dictates validation behavior.
     * @return A {@link PluginValidationResult} containing the accepted plugins,
     *         rejected duplicates if enforcement is enabled, or warnings for duplicates
     *         if enforcement is disabled.
     */
    static <T extends Plugin> PluginValidationResult<PluginHandle<T>> verifyUniqueIdentities(List<PluginHandle<T>> plugins, LoaderConfiguration configuration) {
        // Group all descriptors by normalized identity
        Map<String, List<PluginHandle<T>>> groups = plugins.stream()
            .collect(Collectors.groupingBy(
                entry -> entry.descriptor().normalizedIdentity(),
                Collectors.toList())
            );
        
        Set<PluginHandle<T>> accepted = new HashSet<>();
        Map<PluginHandle<T>, List<LoaderProblem>> duplicates = new HashMap<>();
        
        for (Map.Entry<String, List<PluginHandle<T>>> group : groups.entrySet()) {
            String normalizedIdentity = group.getKey();
            List<PluginHandle<T>> members = group.getValue();
            
            // Get all acceptable plugins that have no duplicates (single entry sets)
            if (members.size() == 1) {
                accepted.add(members.get(0));
            // There are no empty sets possible, so else equals size>1
            } else {
                for (PluginHandle<T> member : members) {
                    // Generate the list of duplication problems but skip for the current plugin
                    List<LoaderProblem> problems = members.stream()
                        .filter(handle -> !handle.equals(member))
                        .map(handle -> new LoaderProblem.DuplicateIdentity(normalizedIdentity, member.descriptor(), handle.descriptor()))
                        .collect(Collectors.toList());
                    
                    duplicates.put(member, problems);
                }
            }
        }
        
        if (configuration.ENFORCE_UNAMBIGUOUS_PLUGIN_IDENTITIES()) {
            // Return duplicates as rejected
            return new PluginValidationResult<>(
                Set.copyOf(accepted),
                PluginValidationResult.copyProblemMap(duplicates),
                Map.of()
            );
        }
        
        // Configuration says we only warn about duplicates
        return new PluginValidationResult<>(
            Set.copyOf(accepted),
            Map.of(),
            PluginValidationResult.copyProblemMap(duplicates)
        );
    }
    
}
