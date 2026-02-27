package io.gdcc.spi.core.test;

import io.gdcc.spi.core.plugin.CoreProvider;

public interface TestProvider extends CoreProvider {

    int API_LEVEL = 100;
    
    static int apiLevel() {
        return API_LEVEL;
    }

}
