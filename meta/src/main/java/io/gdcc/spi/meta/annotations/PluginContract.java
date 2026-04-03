package io.gdcc.spi.meta.annotations;

import io.gdcc.spi.meta.plugin.Plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a versioned plugin contract interface.
 *
 * <p>A plugin contract defines either a directly loadable plugin kind
 * ({@link Role#BASE}) or an additional, non-loadable capability
 * ({@link Role#CAPABILITY}).</p>
 *
 * <p>The annotated type must be an {@code interface} extending {@link Plugin}
 * and must declare a compile-time constant primitive {@code int API_LEVEL} field.
 * </p>
 *
 * <p>General contract rules:</p>
 * <ol>
 *   <li>Plugin contracts may only be declared on interfaces.</li>
 *   <li>Plugin contracts must extend {@link Plugin}.</li>
 *   <li>Plugin contracts may not extend other plugin contracts. (One exception, see below.)</li>
 *   <li>A plugin implementation may implement exactly one {@link Role#BASE base contract}.</li>
 * </ol>
 *
 * <p>Base contracts are used as the unique service-loading identity of a plugin.
 * Capability contracts are never loaded directly; they add optional functionality
 * and are discovered through generated plugin metadata.</p>
 *
 * <p>Capability rules:</p>
 * <ol>
 *   <li>A capability contract must declare {@link #requires()}.</li>
 *   <li>A capability must require exactly one base contract.</li>
 *   <li>A capability may extend the required base contract to provide default implementations.</li>
 *   <li>For now, requiring or extending another capability is not supported.</li>
 *   <li>A plugin implementing a capability must also implement its required base contract.</li>
 * </ol>
 *
 * Note: this annotation cannot be used repeatedly on the same type.
 *
 * @implNote Example base contract:
 * <pre>{@code
 * @PluginContract(role = PluginContract.Role.BASE)
 * public interface FooBar extends Plugin {
 *     int API_LEVEL = 1;
 * }
 * }</pre>
 * Example capability contract:
 * <pre>{@code
 * @PluginContract(
 *     role = PluginContract.Role.CAPABILITY,
 *     requires = { FooBar.class }
 * )
 * public interface BarBeque extends Plugin {
 *     int API_LEVEL = 1;
 *
 *     default String getMediaType() {
 *         return "application/bbq";
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginContract {
    
    /**
     * Declares whether this contract is a directly loadable base contract or an additional capability contract.
     */
    Role role();
    
    /**
     * Other plugin contracts that must also be implemented when this contract is implemented.
     *
     * <p>For {@link Role#CAPABILITY capabilities}, this must currently contain exactly one
     * required {@link Role#BASE base contract}. Capabilities are not directly loadable and
     * therefore must always be paired with their base contract.</p>
     */
    Class<? extends Plugin>[] requires() default {};
    
    /**
     * Core provider contracts required by this plugin contract.
     */
    RequiredProvider[] providers() default {};
    
    /**
     * Distinguishes directly loadable base contracts from additional capability contracts.
     */
    enum Role {
        /**
         * A directly loadable plugin contract.
         */
        BASE,
        
        /**
         * An additional plugin capability that refines behavior but is not directly loadable.
         */
        CAPABILITY
    }
}
