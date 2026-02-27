package io.gdcc.spi.core.plugin;

public interface CoreProvider {
    
    /**
     * Plugins require accessibility to core functionality, which {@link CoreProvider}s offer to them.
     * Any provider implementation in the core requires building against a specific version of the API contract.
     * To avoid plugins asking the wrong questions or the wrong way, the plugin loader will need to check
     * that the core provider implementations use the same API contract level as the plugins.
     *
     * @apiNote This method must be overridden by any provider interface extending this interface and return an API level
     *          inlined at compile-time of the interface.
     * @return the API level the core uses
     * @throws UnsupportedOperationException when the provider interface does not override the method
     */
    static int apiLevel() {
        throw new UnsupportedOperationException("Provider must override apiLevel()");
    }
    
}
