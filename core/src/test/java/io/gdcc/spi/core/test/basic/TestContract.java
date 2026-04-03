package io.gdcc.spi.core.test.basic;

import io.gdcc.spi.meta.annotations.PluginContract;
import io.gdcc.spi.meta.plugin.Plugin;

@PluginContract(role = PluginContract.Role.BASE)
public interface TestContract extends Plugin {
    
    int API_LEVEL = 1;
    
    void test();
}
