package io.gdcc.spi.core.loader;

import io.gdcc.spi.meta.annotations.PluginContract;
import io.gdcc.spi.meta.descriptor.DescriptorScanner;
import io.gdcc.spi.meta.descriptor.PluginDescriptor;
import io.gdcc.spi.meta.descriptor.SourcedDescriptor;
import io.gdcc.spi.meta.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads plugins of a specified type from JAR files in a given directory using the Java ServiceLoader mechanism.
 * <p>
 * Each plugin must implement the {@link Plugin} interface and provide a non-null, non-blank identity via the
 * {@link Plugin#identity()} method. Plugins are loaded from individual JAR files, each loaded in its own
 * {@link URLClassLoader}, enabling isolated class loading for plugin dependencies.
 * </p>
 * <p>
 * This class supports custom ClassLoader hierarchies to accommodate complex deployment environments, and
 * aggregates errors encountered during plugin discovery and loading into a single {@link LoaderException}
 * if no plugins are successfully loaded.
 * </p>
 * <p>
 * It supports one-time classloading, but no reloading of changed JARs at runtime.
 * An application restart is required to pick up changes to plugin JARs.
 * </p>
 *
 * @param <T> the type of plugin to load, constrained to implement the {@link Plugin} interface
 */
public class PluginLoader<T extends Plugin> {
    
    private static final Logger logger = LoggerFactory.getLogger(PluginLoader.class);
    
    private final Class<T> pluginClass;
    private final ClassLoader parentClassLoader;
    private final LoaderConfiguration configuration;
    
    /**
     * Constructs a new PluginLoader that will load plugins of the specified type {@code T}.
     * The parent ClassLoader is set to the current thread's context ClassLoader, which allows
     * plugins to access classes and resources on the core's classpath.
     * It uses the system default configuration for plugin loading behaviors,
     * see {@link LoaderConfiguration#defaults()}.
     *
     * @param pluginClass the Class object representing the plugin type {@code T} to load
     */
    public PluginLoader(Class<T> pluginClass) {
        this(pluginClass, Thread.currentThread().getContextClassLoader());
    }
    
    /**
     * Constructs a new PluginLoader that will load plugins of the specified type {@code T}.
     * It uses the system default configuration for plugin loading behaviors,
     * see {@link LoaderConfiguration#defaults()}.
     *
     * @param pluginClass the Class object representing the plugin type {@code T} to load
     * @param parentClassLoader the ClassLoader to be used as the parent for class loading of plugins
     */
    public PluginLoader(Class<T> pluginClass, ClassLoader parentClassLoader) {
        this(pluginClass, LoaderConfiguration.defaults(), parentClassLoader);
    }
    
    /**
     * Constructs a new PluginLoader that will load plugins of the specified type {@code T}.
     * The parent ClassLoader is set to the current thread's context ClassLoader, which allows
     * plugins to access classes and resources on the core's classpath.
     *
     * @param pluginClass the Class object representing the type of plugin {@code T} to load
     * @param configuration the LoaderConfiguration specifying custom plugin loading behaviors
     */
    public PluginLoader(Class<T> pluginClass, LoaderConfiguration configuration) {
        this(pluginClass, configuration, Thread.currentThread().getContextClassLoader());
    }
    
    /**
     * Constructs a new instance of the PluginLoader, which is responsible for loading plugins of the specified type {@code T}.
     *
     * @param pluginClass the Class object representing the type of plugin {@code T} to load
     * @param configuration the LoaderConfiguration specifying custom plugin loading behaviors
     * @param parentClassLoader the ClassLoader to be used as the parent for loading plugin classes and resources
     */
    public PluginLoader(Class<T> pluginClass, LoaderConfiguration configuration, ClassLoader parentClassLoader) {
        
        // Basic Verification
        Objects.requireNonNull(pluginClass);
        Objects.requireNonNull(configuration);
        Objects.requireNonNull(parentClassLoader);
        this.pluginClass = pluginClass;
        this.configuration = configuration;
        this.parentClassLoader = parentClassLoader;
        
        // Check that the plugin class is a base plugin contract
        validatePluginBaseClass(pluginClass);
    }
    
    /**
     * Validates that the provided class is a valid Dataverse Plugin Interface.
     * The class must be an interface and annotated with {@link PluginContract}.
     *
     * @param pluginClass the class to validate
     * @throws IllegalArgumentException if the class is not an interface or is not annotated with {@code @PluginContract}
     */
    static void validatePluginBaseClass(Class<?> pluginClass) {
        if (!pluginClass.isInterface() ||
            !pluginClass.isAnnotationPresent(PluginContract.class) ||
            pluginClass.getDeclaredAnnotationsByType(PluginContract.class)[0].role() != PluginContract.Role.BASE)
            throw new IllegalArgumentException("Class argument must be a Dataverse Plugin Interface annotated with @PluginContract and have a role of BASE");
    }
    
    /**
     * <p>Loads all plugins of type {@code T} from JAR files located in the specified directory.
     * Each JAR file is loaded using a dedicated {@link URLClassLoader}, and plugins are
     * discovered via the Java {@link ServiceLoader} mechanism (META-INF/services/package.plus.service.ClassName file).
     *
     * <p>For each discovered plugin, its {@link Plugin#identity()} must be non-null and non-blank;
     * otherwise, it is skipped and an error is recorded.
     * </p>
     *
     * @param pluginJarsLocation the directory containing JAR files to scan for plugins
     * @return a list of {@link PluginHandle}, linking each plugin's metadata to the corresponding plugin instance
     * @throws LoaderException if one or more errors occur during loading, if no plugins
     *         could be successfully loaded, or if there are any duplicates.
     *         Note: The exception may contain multiple causes, each associated with a specific file or failure point
     */
    public List<PluginHandle<T>> load(Path pluginJarsLocation) {
        
        // Find all potential sources within the given location
        Map<Path,URL[]> sources = findSources(pluginJarsLocation);
        
        // Preload the plugins (already validating via metadata before handing off to any classloader)
        List<SourcedDescriptor> descriptors = preloadPlugins(sources.keySet());
        
        // Load the pre-validated plugins
        return load(descriptors, sources);
    }
    
    /**
     * Locates plugin files within the given directory (but not subdirectories) and constructs
     * corresponding URL arrays for class loading.
     *
     * @param pluginsLocation the root directory path to search for plugins
     * @return a map where each key is a path to a root classpath (a JAR file or directory) and the corresponding value is
     *         a single-element array containing the generated URL for that location.
     *         Note: for JARs, the URL is of the form "jar:<urlOfPathToJarFile>!/" as required by {@code URLClassLoader}
     * @throws LoaderException if one or more errors occur during directory scanning or URL construction
     *         and no valid mappings could be produced; the exception may contain multiple causes
     *         each associated with a specific file or failure point
     */
    Map<Path,URL[]> findSources(Path pluginsLocation) {
        // Collect as many problems as possible before throwing an exception
        List<LoaderProblem> problems = new ArrayList<>();
        Map<Path,URL[]> classRoots = new HashMap<>();
        
        // Find all JAR files at the given location (ignoring potential subdirectories)
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsLocation, "*.jar")) {
            // Using the foreach loop here to enable catching the URI/URL exceptions
            for (Path path : stream) {
                try {
                    // The URL[] is necessary as classloaders can deal with multiple locations at once.
                    // Note: "jar:<urlToJarFile>!/" is the special syntax required to scan a complete JAR file for classes
                    classRoots.put(path, new URL[]{LoaderHelper.pathToUrl(path, "jar", "!/")});
                // This is not likely to happen, as we construct the URL from a valid path only
                } catch (MalformedURLException e) {
                    problems.add(new LoaderProblem.LocationFailure(path, e));
                }
            }
            
            // In addition: put the directory itself to enable loading exploded archives (mostly useful for testing).
            // The location must be a browsable directory as otherwise exceptions would have been raised.
            classRoots.put(pluginsLocation, new URL[]{LoaderHelper.pathToUrl(pluginsLocation, "", "")});
            
        // NotDirectoryException | AccessDeniedException is a subset of IOException and covers these cases.
        } catch (PatternSyntaxException | IOException e) {
            problems.add(new LoaderProblem.SourceFailure(e));
        }
        
        if (problems.isEmpty()) {
            return classRoots;
        }
        throw new LoaderException(problems);
    }
    
    /**
     * Preloads plugins by scanning the provided source paths and retrieving their descriptors.
     * Uses {@link DescriptorScanner#scanPath(Path)} to scan each source path.
     * @see PluginLoader#preloadPlugins(Set, SourceScanner) for more details.
     *
     * @param sources A set of file paths representing the sources to scan for plugins.
     * @return A list of SourcedDescriptor objects representing the preloaded plugins.
     * @throws LoaderException If an error occurs while scanning the paths or loading plugin descriptors.
     */
    List<SourcedDescriptor> preloadPlugins(Set<Path> sources) throws LoaderException {
        return preloadPlugins(sources, DescriptorScanner::scanPath);
    }
    
    /**
     * Preloads plugin descriptors from the given list of source paths. This method scans the provided
     * sources for plugin descriptors, and validates them against various criteria (such as class name collisions,
     * proper implementations of the desired plugin class, and API compatibility). It returns a set of plugin
     * descriptors either valid or associated with a warning-level incompatibility.
     *
     * <p>Problems encountered during the loading process either trigger an exception to abort loading or
     * are logged only based on configuration settings.</p>
     *
     * @param sources the list of paths to scan for plugin descriptors
     * @return a list of valid plugin descriptors that were successfully scanned and passed all validation checks
     * @throws LoaderException if validation problems are encountered and the configuration mandates an abort
     */
    List<SourcedDescriptor> preloadPlugins(Set<Path> sources, SourceScanner scanner) throws LoaderException {
        // Try to continue as long as possible before erroring out, catching as many problems as possible at once.
        List<LoaderProblem> sourceProblems = new ArrayList<>();
        // Scratch space to collect descriptors from the given sources.
        List<SourcedDescriptor> descriptors = new ArrayList<>();
        
        // 1. Grab all the plugin descriptors from the given sources
        for (Path source : sources) {
            try {
                descriptors.addAll(scanner.scanPath(source));
            } catch (IOException e) {
                sourceProblems.add(new LoaderProblem.LocationFailure(source, e));
                logger.debug("Failed to scan source: {}", source, e);
            }
        }
        logger.debug("Scanning for plugin descriptors found {} plugins: {}", descriptors.size(), descriptors);
        
        // 2. Verify that no class name collisions exist any plugins to be loaded.
        var collisionResult = LoaderHelper.verifyNoClassCollisions(descriptors, this.parentClassLoader);
        logger.debug("Scanning for class name collisions results: {}", collisionResult);
        
        // 3. Filter the descriptors to only include those that implement the desired plugin (base) class.
        var implementationResult = LoaderHelper.identifyNonImplementations(descriptors, this.pluginClass, this.configuration);
        logger.debug("Scanning for non-implementations results: {}", implementationResult);
        
        // 4. Verify that every plugin class has a service loader entry. Remove any affected from the list.
        var serviceProviderResult = LoaderHelper.verifyServiceProviderRecords(descriptors);
        logger.debug("Scanning for SPI record results: {}", serviceProviderResult);
        
        // 5. Verify that the API level of the plugin matches the core-expected level(s).
        var apiLevelResult = LoaderHelper.verifyPluginApiLevels(descriptors, this.pluginClass, this.parentClassLoader);
        logger.debug("Scanning for plugin API level matches results: {}", apiLevelResult);
        
        // 6. Verify all the provider requirements by the plugin are met
        var providerLevelsResult = LoaderHelper.verifyProviderApiLevels(descriptors, this.parentClassLoader);
        logger.debug("Scanning for provider API level matches results: {}", apiLevelResult);
        
        // Merge all the different results to receive the final picture which plugins are faulty
        var finalResults = PluginValidationResult.merge(
            collisionResult,
            implementationResult,
            serviceProviderResult,
            apiLevelResult,
            providerLevelsResult
        );
        // Merge all the problems into one large list, to be wrapped in an exception
        finalResults.rejected().forEach((descriptor, problems) -> sourceProblems.addAll(problems));
        
        // By default, we should abort now. In case we are asked to keep going by configuration,
        // let the logs show any found problems as warnings.
        if (configuration.abortOnCompatibilityProblems() && (!sourceProblems.isEmpty() || !finalResults.rejected().isEmpty())) {
            throw new LoaderException(sourceProblems);
        }
        
        logger.warn("Pre-loading validation failed for {} plugins with {} problems, continuing with {} valid and {} warning plugins as requested.",
            finalResults.rejected().size() + finalResults.warning().size(),
            sourceProblems.size(),
            finalResults.accepted().size(),
            finalResults.warning().size());
        sourceProblems.forEach(problem -> logger.warn(problem.message()));
        finalResults.warning().forEach((descriptor, problems) ->
            logger.warn("Plugin {} has {} potential compatibility problems: {}",
                descriptor,
                problems.size(),
                problems.stream().map(LoaderProblem::message).collect(Collectors.joining(", "))
        ));
        
        return Stream.concat(finalResults.accepted().stream(), finalResults.warning().keySet().stream()).toList();
    }
    
    
    /**
     * Loads plugins of type {@code T} from the specified mapping of locations to JAR URLs.
     * Each location is processed by creating a dedicated {@link URLClassLoader}, and plugins are
     * discovered via the Java {@link ServiceLoader} mechanism using the configured plugin class.
     *
     * For each discovered plugin, its {@link Plugin#identity()} must be non-null and non-blank;
     * otherwise, it is skipped and an error is recorded.
     *
     * The returned map's keys describe the source of each loaded plugin via {@link PluginDescriptor},
     * associating the plugin's logical identity, class name, and JAR file location. It is the
     * caller's responsibility to verify no duplicates (by class name or identity) exist before
     * handing the plugins to the core.
     *
     * @param sources a mapping from (JAR) file paths to their corresponding URLs used for class loading
     * @return a map from {@link PluginDescriptor} metadata to the corresponding plugin instance
     * @throws LoaderException if one or more errors occur during loading and no plugins
     *         could be successfully loaded; the exception may contain multiple causes,
     *         each associated with a specific JAR file or failure point
     */
    List<PluginHandle<T>> load(List<SourcedDescriptor> descriptors, Map<Path,URL[]> sources) {
        List<LoaderProblem> sourceProblems = new ArrayList<>();
        List<PluginHandle<T>> loadedPlugins = new ArrayList<>();
        
        // Create URLClassLoader for each file and load the plugin
        descriptors.forEach(descriptor -> {
            URL[] sourceUrl = sources.get(descriptor.sourceLocation());
            try (URLClassLoader classLoader = URLClassLoader.newInstance(sourceUrl, this.parentClassLoader)) {
                // Load all plugins that can be found within the source for type T
                ServiceLoader<T> loader = ServiceLoader.load(this.pluginClass, classLoader);
                
                // Iterate over all found plugins and add to the plugin map, including source information
                loader.forEach(plugin -> {
                    String identity = plugin.identity();
                    if (identity == null || identity.isBlank()) {
                        sourceProblems.add(new LoaderProblem.LocationFailure(
                            descriptor.sourceLocation(),
                            new IllegalArgumentException(plugin.getClass().getCanonicalName() + "'s identity cannot be null or blank")));
                        return;
                    }
                    
                    // Save the plugin and its metadata to the set of already loaded plugins
                    loadedPlugins.add(
                        new PluginHandle<>(
                            LoaderHelper.toPluginDescriptor(
                                descriptor,
                                plugin,
                                this.parentClassLoader),
                            plugin)
                    );
                });
            } catch (IOException | NoSuchMethodError | ServiceConfigurationError | UnsupportedClassVersionError e) {
                sourceProblems.add(new LoaderProblem.LocationFailure(descriptor.sourceLocation(), e));
            }
        });
        logger.debug("Loader was able to load {} plugins from {} sources.", loadedPlugins.size(), sources.size());
        
        // Make sure there are no duplicate plugin identities
        PluginValidationResult<PluginHandle<T>> duplicationChecks = LoaderHelper.verifyUniqueIdentities(loadedPlugins, configuration);
        
        // Merge all the different results to receive the final picture which plugins are faulty
        // (For now, we only have a single check at this stage)
        var finalResults = PluginValidationResult.merge(duplicationChecks);
        
        // Merge all the problems into one large list, to be wrapped in an exception
        finalResults.rejected().forEach((descriptor, problems) -> sourceProblems.addAll(problems));
        
        // By default, we should abort now. In case we are asked to keep going by configuration,
        // let the logs show any found problems as warnings.
        if (configuration.abortOnCompatibilityProblems() && (!sourceProblems.isEmpty() || !finalResults.rejected().isEmpty())) {
            throw new LoaderException(sourceProblems);
        }
        
        logger.warn("Validation after loading failed for {} plugins with {} problems, continuing with {} valid and {} warning plugins as requested.",
            finalResults.rejected().size() + finalResults.warning().size(),
            sourceProblems.size(),
            finalResults.accepted().size(),
            finalResults.warning().size());
        sourceProblems.forEach(problem -> logger.warn(problem.message()));
        finalResults.warning().forEach((descriptor, problems) ->
            logger.warn("Plugin {} has {} potential problems: {}",
                descriptor,
                problems.size(),
                problems.stream().map(LoaderProblem::message).collect(Collectors.joining(", "))
            ));
        
        return Stream.concat(finalResults.accepted().stream(), finalResults.warning().keySet().stream()).toList();
    }
    
}
