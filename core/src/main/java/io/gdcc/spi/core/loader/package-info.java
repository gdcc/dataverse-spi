/**
 * Provides the runtime plugin loading facilities for the SPI-based plugin system.
 *
 * <h2>Overview</h2>
 *
 * <p>This package is responsible for discovering, validating, and instantiating plugins
 * for a specific base plugin contract. The central entry point is {@link io.gdcc.spi.core.loader.PluginLoader},
 * which loads plugins from a filesystem location containing plugin artifacts such as JARs
 * or exploded directories.</p>
 *
 * <p>The loader is intentionally validation-first:</p>
 * <ol>
 *   <li>It discovers candidate plugin sources from a given location.</li>
 *   <li>It scans descriptor metadata from those sources.</li>
 *   <li>It validates the metadata before plugin classes are instantiated.</li>
 *   <li>It loads only the descriptors that passed validation, or those explicitly allowed
 *       by configuration as warning-level cases.</li>
 *   <li>It returns loaded plugins as {@link io.gdcc.spi.core.loader.PluginHandle} instances, pairing the runtime
 *       plugin instance with its resolved {@link io.gdcc.spi.meta.descriptor.PluginDescriptor metadata}.</li>
 * </ol>
 *
 * <p>This design keeps failure handling predictable and avoids loading plugin classes
 * unnecessarily when metadata already shows that a plugin is incompatible.</p>
 *
 * <h2>How the loader works</h2>
 *
 * <p>A {@link io.gdcc.spi.core.loader.PluginLoader} is created for exactly one base plugin contract. That contract
 * must be a valid plugin interface intended to act as the loader's target type.</p>
 *
 * <p>Calling {@link io.gdcc.spi.core.loader.PluginLoader#load(java.nio.file.Path)} performs two broad phases:</p>
 *
 * <h3>1. Preload and validation</h3>
 *
 * <p>During preloading, the loader scans the configured sources and reads plugin descriptors.
 * It then validates, among other things:</p>
 * <ul>
 *   <li>class name collisions between plugins,</li>
 *   <li>class name collisions with classes already present in core,</li>
 *   <li>whether a descriptor matches the requested base contract,</li>
 *   <li>base contract API level compatibility,</li>
 *   <li>required provider compatibility, and</li>
 *   <li>identity uniqueness rules, depending on configuration.</li>
 * </ul>
 *
 * <p>Validation problems are represented as {@link io.gdcc.spi.core.loader.LoaderProblem} instances. When the active
 * {@link io.gdcc.spi.core.loader.LoaderConfiguration} is strict, incompatible plugins cause loading to abort with
 * a {@link io.gdcc.spi.core.loader.LoaderException}. In more permissive configurations, some problems may be treated
 * as warnings and the loader may continue with the remaining valid plugins.</p>
 *
 * <h3>2. Class loading and instantiation</h3>
 *
 * <p>After successful prevalidation, plugin implementation classes are loaded from their
 * corresponding source locations and instantiated. Each successfully loaded plugin is returned
 * as a {@link io.gdcc.spi.core.loader.PluginHandle}, which gives access both to the instantiated plugin and to the
 * resolved runtime descriptor.</p>
 *
 * <h2>Applying configuration options</h2>
 *
 * <p>Loader behavior is controlled through {@link io.gdcc.spi.core.loader.LoaderConfiguration}. The configuration
 * type is immutable. The recommended style is to start from {@link io.gdcc.spi.core.loader.LoaderConfiguration#defaults()}
 * and customize only the options that need to change.</p>
 *
 * <p>Typical configuration concerns include:</p>
 * <ul>
 *   <li>whether sources must contain only plugins matching the requested base contract,</li>
 *   <li>whether mixed sources should emit warnings,</li>
 *   <li>whether compatibility problems should abort loading, and</li>
 *   <li>whether ambiguous or duplicated plugin identities should be rejected.</li>
 * </ul>
 *
 * <p>Because configuration instances are immutable, they are easy to reuse, share, and test.</p>
 *
 * <h3>Example: customizing loader behavior</h3>
 *
 * <pre>{@code
 * LoaderConfiguration configuration = LoaderConfiguration.defaults()
 *     .withEnforceSingleSourceMatchingPluginsOnly(false)
 *     .withEmitWarningsOnMultiPluginSource(true)
 *     .withAbortOnCompatibilityProblems(false);
 *
 * PluginLoader<MyPluginContract> loader =
 *     new PluginLoader<>(MyPluginContract.class, configuration);
 * }</pre>
 *
 * <h2>What to expect</h2>
 *
 * <ul>
 *   <li><strong>Early validation:</strong> many incompatibilities are detected from metadata
 *       before plugin classes are instantiated.</li>
 *   <li><strong>Aggregated diagnostics:</strong> failures are reported as collections of
 *       {@link io.gdcc.spi.core.loader.LoaderProblem problems} through {@link io.gdcc.spi.core.loader.LoaderException#getProblems()}.</li>
 *   <li><strong>Contract-focused loading:</strong> each loader instance targets one base
 *       plugin contract at a time.</li>
 *   <li><strong>Structured runtime results:</strong> successful loads are represented by
 *       {@link io.gdcc.spi.core.loader.PluginHandle} values rather than raw plugin instances alone.</li>
 *   <li><strong>Configurable strictness:</strong> callers can choose between fail-fast and
 *       more permissive behavior depending on operational needs.</li>
 * </ul>
 *
 * <h2>What not to expect</h2>
 *
 * <ul>
 *   <li><strong>No hot reload:</strong> this package supports loading, not live reloading of
 *       changed plugin artifacts. Updated plugins generally require a fresh loading cycle and
 *       typically an application restart strategy at a higher level.</li>
 *   <li><strong>No legacy-plugin compatibility guarantee:</strong> the loader operates on the
 *       descriptor-based plugin model and does not aim to support older plugins that do not
 *       participate in that model.</li>
 *   <li><strong>No cross-loader identity coordination:</strong> identity uniqueness is checked
 *       within the scope of a loader run, not globally across every possible plugin source in
 *       an application.</li>
 *   <li><strong>No substitute for packaging discipline:</strong> plugin authors are still
 *       expected to provide correct metadata, compatible API levels, and non-conflicting
 *       classes and identities.</li>
 * </ul>
 *
 * <h2>Usage examples</h2>
 *
 * <h3>Example 1: Loading plugins with default settings</h3>
 *
 * <pre>{@code
 * try {
 *     PluginLoader<MyPluginContract> loader = new PluginLoader<>(MyPluginContract.class);
 *     List<PluginHandle<MyPluginContract>> plugins = loader.load(Path.of("plugins"));
 *
 *     // Use or store plugins.
 *
 * } catch (LoaderException ex) {
 *     for (LoaderProblem problem : ex.getProblems()) {
 *         System.err.println(problem.message());
 *     }
 * } catch (IllegalArgumentException ex) {
 *     System.err.println(ex.getMessage());
 * }
 * }</pre>
 *
 * <h3>Example 2: Using an explicit parent class loader</h3>
 *
 * <pre>{@code
 * ClassLoader parent = Thread.currentThread().getContextClassLoader();
 * PluginLoader<MyPluginContract> loader = new PluginLoader<>(MyPluginContract.class, parent);
 * }</pre>
 *
 * <p>In most applications, callers only need {@link io.gdcc.spi.core.loader.PluginLoader} and
 * {@link io.gdcc.spi.core.loader.LoaderConfiguration}. The remaining types in this package primarily support
 * diagnostics, runtime result transport, and internal validation mechanics.</p>
 */
package io.gdcc.spi.core.loader;