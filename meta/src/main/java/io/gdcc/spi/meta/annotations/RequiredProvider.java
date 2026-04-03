package io.gdcc.spi.meta.annotations;

import io.gdcc.spi.meta.plugin.CoreProvider;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Declares that a {@link PluginContract} requires a specific core provider contract.
 *
 * <p>The provider API level is taken from the provider interface's
 * {@code API_LEVEL} constant at compile time by the annotation processor.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredProvider {
    Class<? extends CoreProvider> value();
}
