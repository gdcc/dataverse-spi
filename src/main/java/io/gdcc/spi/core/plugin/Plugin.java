package io.gdcc.spi.core.plugin;

import java.util.Set;

public interface Plugin {
    
    /**
     * Returns the unique, machine-readable identifier for this plugin.
     * This will be the primary key within the core to identify a specific plugin implementation.
     *
     * @return the plugin's identity string, which must be non-null, non-blank, and URL compatible.
     * @implSpec This method must be overridden by any plugin implementation and return a non-null, non-blank,
     *           URL-compatible string. No plugin interface may provide a default implementation.
     */
    String identity();
    
    /**
     * Returns the plugin API level that this plugin has been built against to the core system.
     * This represents the version of the plugin contract that the plugin implementation
     * adheres to, used by the core loader to ensure compatibility between the plugin
     * and the core system.
     *
     * @return the API level provided by this plugin
     * @implSpec This method must be overridden by any plugin implementation and return an API level
     *           inlined at compile-time of the interface. A plugin interface may provide a default implementation,
     *           but must be aware this has to be dropped once the initial API level needs to be increased due
     *           to a breaking change.
     * @implNote Inlining the API level at build time requires this method body to return the primitive constant
     *           from the plugin interface. If the plugin interface uses a {@code int API_LEVEL}
     *           (always {@code static final} constants in interfaces), an example code would look like this:
     *           {@code return PutPluginInterfaceNameHere.API_LEVEL; }
     */
    int providedPluginApiLevel();
    
    /**
     * A plugin interacts with the core using any number of providers.
     * When a plugin is built, it is linked against a specific version of the {@link CoreProvider} contracts.
     * At loading time, the core must ensure that the plugin will use the same API contract level as the provider
     * implementations in the core expect.
     * A loader uses this method to determine of a specific provider API level used at build-time of the plugin.
     *
     * @return the required API level for the given provider class the plugin expects the core to support
     * @throws IllegalArgumentException when a given provider class is not needed or unknown to the plugin
     * @implSpec This method must be overridden by any plugin implementation and return an API level
     *           inlined at compile-time of the interface. A plugin interface may provide a default implementation,
     *           but must be aware this has to be dropped once the initial API level needs to be increased due
     *           to a breaking change.
     */
    int requiredProviderApiLevel(Class<CoreProvider> providerClass);
    
    /**
     * Returns the set of {@link CoreProvider} interfaces that this plugin expects to be available
     * at runtime for interacting with the core system.
     * The core uses this information to validate compatibility and ensure all required provider contracts
     * are present and match the API level used during plugin compilation.
     * See also {@link #requiredProviderApiLevel(Class)} for details on API level compatibility.
     *
     * @return a non-null set of {@link CoreProvider} interface classes expected by the plugin
     * @implSpec This method must be overridden by any plugin implementation and return a set of
     *           {@link CoreProvider} interface classes that the plugin expects to be available at runtime.
     *           The set may be empty if no providers are in use. The plugin interface may provide a
     *           default implementation, but must drop it once a breaking change for this method appears.
     */
    Set<Class<? extends CoreProvider>> expectedProviders();
    
}
