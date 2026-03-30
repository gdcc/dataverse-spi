package io.gdcc.spi.meta.processor;

import io.gdcc.spi.meta.annotations.DataversePlugin;
import io.gdcc.spi.meta.annotations.PluginContract;
import io.gdcc.spi.meta.annotations.RequiredProvider;
import io.gdcc.spi.meta.plugin.CoreProvider;
import io.gdcc.spi.meta.plugin.Plugin;

public class ProcessorConstants {
    
    private ProcessorConstants() {
        /* Intentionally left blank for singleton */
    }
    
    /**
     * Name of the compile-time constant field carrying the contract version.
     */
    public static final String API_LEVEL_FIELD_NAME = "API_LEVEL";
    
    /**
     * Fully qualified name of the implementation marker annotation.
     *
     * <p>A string constant is used instead of a direct class literal so this processor can stay
     * tolerant during bootstrapping and module boundary changes.</p>
     *
     * @see io.gdcc.spi.meta.annotations.DataversePlugin
     */
    public static final String PLUGIN_IMPLEMENTATION_ANNOTATION = DataversePlugin.class.getName();
    
    /**
     * Fully qualified name of the contract annotation found on plugin contract interfaces.
     *
     * @see io.gdcc.spi.meta.annotations.PluginContract
     */
    public static final String PLUGIN_CONTRACT_ANNOTATION = PluginContract.class.getName();
    
    /**
     * Fully qualified name of the nested provider requirement annotation used inside
     * {@code @PluginContract.providers()}.
     *
     * @see io.gdcc.spi.meta.annotations.RequiredProvider
     */
    public static final String REQUIRED_PROVIDER_ANNOTATION = RequiredProvider.class.getName();
    
    /**
     * Fully qualified name of {@code @AutoService}.
     *
     * <p>The processor does not depend on AutoService directly. It merely detects the annotation by
     * name so it can avoid generating conflicting ServiceLoader resources.</p>
     */
    public static final String AUTO_SERVICE_ANNOTATION = "com.google.auto.service.AutoService";
    
    /**
     * Fully qualified name of the common plugin super-interface.
     * @see io.gdcc.spi.meta.plugin.Plugin
     */
    public static final String PLUGIN_INTERFACE = Plugin.class.getName();
    
    /**
     * Fully qualified name of the common provider super-interface.
     * @see io.gdcc.spi.meta.plugin.CoreProvider
     */
    public static final String CORE_PROVIDER_INTERFACE = CoreProvider.class.getName();
}
