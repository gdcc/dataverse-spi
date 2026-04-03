package io.gdcc.spi.core.test.basic;

import io.gdcc.spi.meta.plugin.Plugin;

public class TestPlugin implements TestContract {
    @Override
    public String identity() {
        return "test";
    }
    
    @Override
    public void test() {
        /* Intentionally left blank */
    }
}
