package io.gdcc.spi.meta.annotations;

import io.gdcc.spi.meta.plugin.Plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that an SPI interface is a versioned plugin contract.
 *
 * <p>The contract API level is taken from the interface's {@code API_LEVEL}
 * constant by the annotation processor.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginContract {

    /**
     * Whether this contract is the primary plugin kind or an optional capability.
     */
    Role role();

    /**
     * Other plugin contracts that must also be implemented if this contract is implemented.
     * Example: a {@link Role#CAPABILITY} contract should ask for a {@link Role#BASE} contract to be implemented.
     */
    Class<? extends Plugin>[] requires() default {};

    /**
     * Core providers required by this contract.
     */
    RequiredProvider[] providers() default {};
    
    /**
     * Distinguishes a base plugin contract from optional capability contracts.
     */
    enum Role {
        BASE,
        CAPABILITY
    }
}
