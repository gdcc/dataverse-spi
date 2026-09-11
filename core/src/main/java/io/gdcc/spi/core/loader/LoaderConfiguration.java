package io.gdcc.spi.core.loader;


/**
 * Immutable configuration controlling the behavior of the plugin loader.
 *
 * <p>Use {@link #defaults()} to start from the standard configuration and then
 * adjust individual options with the fluent {@code with...} methods.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * LoaderConfiguration configuration = LoaderConfiguration.defaults()
 *     .withEmitWarningsOnMultiPluginSource(true)
 *     .withAbortOnCompatibilityProblems(false);
 * }</pre>
 */
public final class LoaderConfiguration {
    
    private final boolean enforceSingleSourceMatchingPluginsOnly;
    private final boolean emitWarningsOnMultiPluginSource;
    private final boolean abortOnCompatibilityProblems;
    private final boolean abortOnDuplicatedIdentities;
    private final boolean enforceUnambiguousPluginIdentities;
    
    private LoaderConfiguration(
        boolean enforceSingleSourceMatchingPluginsOnly,
        boolean emitWarningsOnMultiPluginSource,
        boolean abortOnCompatibilityProblems,
        boolean abortOnDuplicatedIdentities,
        boolean enforceUnambiguousPluginIdentities
    ) {
        this.enforceSingleSourceMatchingPluginsOnly = enforceSingleSourceMatchingPluginsOnly;
        this.emitWarningsOnMultiPluginSource = emitWarningsOnMultiPluginSource;
        this.abortOnCompatibilityProblems = abortOnCompatibilityProblems;
        this.abortOnDuplicatedIdentities = abortOnDuplicatedIdentities;
        this.enforceUnambiguousPluginIdentities = enforceUnambiguousPluginIdentities;
    }
    
    /**
     * Returns the standard loader configuration (which is strictly enforcing).
     *
     * <ul>
     *   <li>{@code enforceSingleSourceMatchingPluginsOnly = false}</li>
     *   <li>{@code emitWarningsOnMultiPluginSource = false}</li>
     *   <li>{@code abortOnCompatibilityProblems = true}</li>
     *   <li>{@code abortOnDuplicatedIdentities = true}</li>
     *   <li>{@code enforceUnambiguousPluginIdentities = true}</li>
     * </ul>
     */
    public static LoaderConfiguration defaults() {
        return new LoaderConfiguration(
            true,
            false,
            true,
            true,
            true
        );
    }
    
    /**
     * Returns a permissive loader configuration with all strict validation features disabled.
     * It has package private visibility as the only permissive usage is in a testing context.
     *
     * <p>
     * The configuration has the following properties:
     * <ul>
     * <li>{@code enforceSingleSourceMatchingPluginsOnly = false}</li>
     * <li>{@code emitWarningsOnMultiPluginSource = false}</li>
     * <li>{@code abortOnCompatibilityProblems = false}</li>
     * <li>{@code abortOnDuplicatedIdentities = false}</li>
     * <li>{@code enforceUnambiguousPluginIdentities = false}</li>
     * </ul>
     *
     * @return a {@code LoaderConfiguration} instance with permissive settings.
     */
    static LoaderConfiguration permissive() {
        return new LoaderConfiguration(
            false,
            false,
            false,
            false,
            false
        );
    }
    
    /**
     * When enabled, a source may only provide plugins for a single requested base contract.
     * If any non-matching plugin is found, loading from that source is aborted entirely.
     *
     * <p>When disabled, non-matching plugins are ignored.</p>
     */
    public boolean enforceSingleSourceMatchingPluginsOnly() {
        return enforceSingleSourceMatchingPluginsOnly;
    }
    
    /**
     * Returns a copy with {@link #enforceSingleSourceMatchingPluginsOnly()} updated.
     */
    public LoaderConfiguration withEnforceSingleSourceMatchingPluginsOnly(boolean value) {
        return new LoaderConfiguration(
            value,
            emitWarningsOnMultiPluginSource,
            abortOnCompatibilityProblems,
            abortOnDuplicatedIdentities,
            enforceUnambiguousPluginIdentities
        );
    }
    
    /**
     * When {@link #enforceSingleSourceMatchingPluginsOnly()} is disabled, controls whether
     * multi-plugin-contract sources should emit warnings.
     */
    public boolean emitWarningsOnMultiPluginSource() {
        return emitWarningsOnMultiPluginSource;
    }
    
    /**
     * Returns a copy with {@link #emitWarningsOnMultiPluginSource()} updated.
     */
    public LoaderConfiguration withEmitWarningsOnMultiPluginSource(boolean value) {
        return new LoaderConfiguration(
            enforceSingleSourceMatchingPluginsOnly,
            value,
            abortOnCompatibilityProblems,
            abortOnDuplicatedIdentities,
            enforceUnambiguousPluginIdentities
        );
    }
    
    /**
     * When enabled, plugin loading aborts on discovered compatibility problems (for example, API level mismatches).
     * No classes are actually loaded, problems are detected using plugin metadata only.
     */
    public boolean abortOnCompatibilityProblems() {
        return abortOnCompatibilityProblems;
    }
    
    /**
     * Returns a copy with {@link #abortOnCompatibilityProblems()} updated.
     */
    public LoaderConfiguration withAbortOnCompatibilityProblems(boolean value) {
        return new LoaderConfiguration(
            enforceSingleSourceMatchingPluginsOnly,
            emitWarningsOnMultiPluginSource,
            value,
            abortOnDuplicatedIdentities,
            enforceUnambiguousPluginIdentities
        );
    }
    
    /**
     * When enabled, loading aborts if duplicate plugin identities are detected.
     *
     * <p>Note: duplicated identities make plugins undistinguishable for users.</p>
     */
    public boolean abortOnDuplicatedIdentities() {
        return abortOnDuplicatedIdentities;
    }
    
    /**
     * Returns a copy with {@link #abortOnDuplicatedIdentities()} updated.
     */
    public LoaderConfiguration withAbortOnDuplicatedIdentities(boolean value) {
        return new LoaderConfiguration(
            enforceSingleSourceMatchingPluginsOnly,
            emitWarningsOnMultiPluginSource,
            abortOnCompatibilityProblems,
            value,
            enforceUnambiguousPluginIdentities
        );
    }
    
    /**
     * When enabled, plugin identities must be unique within a source.
     * Any plugin's identity that differs by case or special chars only will be seen as a duplicate.
     */
    public boolean enforceUnambiguousPluginIdentities() {
        return enforceUnambiguousPluginIdentities;
    }
    
    /**
     * Returns a copy with {@link #enforceUnambiguousPluginIdentities()} updated.
     */
    public LoaderConfiguration withEnforceUnambiguousPluginIdentities(boolean value) {
        return new LoaderConfiguration(
            enforceSingleSourceMatchingPluginsOnly,
            emitWarningsOnMultiPluginSource,
            abortOnCompatibilityProblems,
            abortOnDuplicatedIdentities,
            value
        );
    }
}


