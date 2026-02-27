package io.gdcc.spi.core.loader;

import io.gdcc.spi.core.plugin.Plugin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

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
    
    private final Class<T> pluginClass;
    private final ClassLoader parentClassLoader;
    
    /**
     * Constructs a new PluginLoader that will load plugins of the specified type {@code T}.
     * The parent ClassLoader is set to the current thread's context ClassLoader, which allows
     * plugins to access classes and resources on the core's classpath.
     *
     * @param pluginClass the Class object representing the plugin type {@code T} to load
     */
    public PluginLoader(Class<T> pluginClass) {
        this(pluginClass, Thread.currentThread().getContextClassLoader());
    }
    
    /**
     * Constructs a new PluginLoader that will load plugins of the specified type {@code T}.
     *
     * @param pluginClass the Class object representing the plugin type {@code T} to load
     * @param parentClassLoader the ClassLoader to be used as the parent for class loading of plugins
     */
    public PluginLoader(Class<T> pluginClass, ClassLoader parentClassLoader) {
        this.pluginClass = pluginClass;
        this.parentClassLoader = parentClassLoader;
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
     * @return a map, linking {@link PluginSource} metadata to the corresponding plugin instance
     * @throws LoaderException if one or more errors occur during loading, if no plugins
     *         could be successfully loaded, or if there are any duplicates.
     *         Note: The exception may contain multiple causes, each associated with a specific file or failure point
     */
    public Map<PluginSource,T> load(Path pluginJarsLocation) {
        // Find and load plugins from JAR sources.
        // "jar:<urlToJarFile>!/" is the syntax required to scan a complete JAR file for classes
        Map<PluginSource,T> plugins = load(findSources(pluginJarsLocation));
        
        // Make sure there are no duplicate plugins
        List<LoaderProblem> problems = new ArrayList<>();
        Set<Set<PluginSource>> duplicateGroups = PluginSource.groupDuplicates(plugins.keySet());
        if (!duplicateGroups.isEmpty()) {
            duplicateGroups.forEach(group -> problems.add(new LoaderProblem.DuplicateSources(group)));
        }
        
        // TODO: add verification logic for API levels and provider services
        
        if (problems.isEmpty()) {
            return plugins;
        } else {
            throw new LoaderException(problems);
        }
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
                    classRoots.put(path, new URL[]{pathToUrl(path, "jar", "!/")});
                // This is not likely to happen, as we construct the URL from a valid path only
                } catch (MalformedURLException e) {
                    problems.add(new LoaderProblem.LocationFailure(path, e));
                }
            }
            
            // In addition: put the directory itself to enable loading exploded archives (mostly useful for testing).
            // The location must be a browsable directory as otherwise exceptions would have been raised.
            classRoots.put(pluginsLocation, new URL[]{pathToUrl(pluginsLocation, "", "")});
            
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
     * Loads plugins of type {@code T} from the specified mapping of locations to JAR URLs.
     * Each location is processed by creating a dedicated {@link URLClassLoader}, and plugins are
     * discovered via the Java {@link ServiceLoader} mechanism using the configured plugin class.
     *
     * For each discovered plugin, its {@link Plugin#identity()} must be non-null and non-blank;
     * otherwise, it is skipped and an error is recorded.
     *
     * The returned map's keys describe the source of each loaded plugin via {@link PluginSource},
     * associating the plugin's logical identity, class name, and JAR file location. It is the
     * caller's responsibility to verify no duplicates (by class name or identity) exist before
     * handing the plugins to the core.
     *
     * @param sources a mapping from (JAR) file paths to their corresponding URLs used for class loading
     * @return a map from {@link PluginSource} metadata to the corresponding plugin instance
     * @throws LoaderException if one or more errors occur during loading and no plugins
     *         could be successfully loaded; the exception may contain multiple causes,
     *         each associated with a specific JAR file or failure point
     */
    Map<PluginSource,T> load(Map<Path,URL[]> sources) {
        List<LoaderProblem> problems = new ArrayList<>();
        Map<PluginSource, T> loadedPlugins = new HashMap<>();
        
        // Create URLClassLoader for each file and load the plugin
        sources.forEach((location, sourceUrl) -> {
            try (URLClassLoader classLoader = URLClassLoader.newInstance(sourceUrl, this.parentClassLoader)) {
                // Load all plugins that can be found within the source for type T
                ServiceLoader<T> loader = ServiceLoader.load(this.pluginClass, classLoader);
                
                // Iterate over all found plugins and add to the plugin map, including source information
                loader.forEach(plugin -> {
                    String identity = plugin.identity();
                    if (identity == null || identity.isBlank()) {
                        problems.add(new LoaderProblem.LocationFailure(location, new IllegalArgumentException(plugin.getClass().getCanonicalName() + "'s identity cannot be null or blank")));
                        return;
                    }
                    
                    // Save the plugin source metadata and put the loaded plugin into the map
                    PluginSource source = new PluginSource(location, plugin.getClass().getCanonicalName(), identity);
                    loadedPlugins.put(source, plugin);
                });
            } catch (IOException | NoSuchMethodError e) {
                problems.add(new LoaderProblem.LocationFailure(location, e));
            }
        });
        
        if (problems.isEmpty()) {
            return loadedPlugins;
        }
        throw new LoaderException(problems);
    }
    
    static URL pathToUrl(Path path, String urlPrefix, String urlSuffix) throws MalformedURLException {
        return new URL(
            (urlPrefix == null || urlPrefix.isBlank() ? "" : urlPrefix + ":" ) +
            path.toUri().toURL() +
            ( urlSuffix == null || urlSuffix.isBlank() ? "" : urlSuffix )
        );
    }
    
}
