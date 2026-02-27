package io.gdcc.spi.core.test;

import io.gdcc.spi.core.plugin.CoreProvider;
import io.gdcc.spi.core.plugin.Plugin;

import java.util.Set;

public class TestPlugin implements Plugin {
    @Override
    public String identity() {
        return "test";
    }
    
    @Override
    public int providedPluginApiLevel() {
        return 1;
    }
    
    @Override
    public int requiredProviderApiLevel(Class<CoreProvider> providerClass) {
        return -1;
    }
    
    @Override
    public Set<Class<? extends CoreProvider>> expectedProviders() {
        return Set.of(TestProvider.class);
    }
}
