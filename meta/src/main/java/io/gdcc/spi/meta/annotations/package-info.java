/**
 * Annotations used to declare Dataverse plugin implementations, plugin contracts,
 * and required core providers.
 *
 * <h2>For plugin implementors</h2>
 *
 * <p>The primary entry point in this package is
 * {@link io.gdcc.spi.meta.annotations.DataversePlugin @DataversePlugin}.
 * Plugin implementation classes intended for discovery and loading by Dataverse
 * should declare this annotation.
 *
 * <p>A plugin implementation must implement exactly one
 * {@linkplain io.gdcc.spi.meta.annotations.PluginContract.Role#BASE base contract}
 * and may additionally implement compatible
 * {@linkplain io.gdcc.spi.meta.annotations.PluginContract.Role#CAPABILITY capability contracts}.
 * Only the base contract serves as the direct loading identity of the plugin.
 *
 * <h3>Example: Plugin side</h3>
 * <p>(The implemented interface are from the example below)
 *
 * <pre>{@code
 *  * @DataversePlugin
 *  * public class Grill implements FooBar, BarBeque {
 *  *     // no override needed unless another default conflicts
 *  * }
 *  * }</pre>
 *
 * <h2>For SPI authors and maintainers</h2>
 *
 * <p>SPI contracts are declared with
 * {@link io.gdcc.spi.meta.annotations.PluginContract @PluginContract}.
 * A base contract defines the unique, directly loadable plugin kind. A capability
 * contract defines additional optional behavior and is never loaded directly.</p>
 *
 * <p>Contract interfaces must extend {@link io.gdcc.spi.meta.plugin.Plugin},
 * declare {@link io.gdcc.spi.meta.annotations.PluginContract}, and provide a
 * compile-time {@code int API_LEVEL} constant. Plugin contracts must not extend
 * other plugin contracts, except that a capability may extend its required base
 * contract.</p>
 *
 * <p>Required Dataverse infrastructure dependencies are declared with
 * {@link io.gdcc.spi.meta.annotations.RequiredProvider @RequiredProvider},
 * which identifies core provider contracts needed by a plugin contract.</p>
 *
 * <p>Capabilities are attached to a plugin through normal Java interface
 * implementation. This allows SPI authors to define additional methods and
 * default implementations without introducing ambiguity into plugin loading.
 * If multiple implemented interfaces contribute conflicting default methods,
 * the plugin implementation class must resolve that conflict explicitly.</p>
 *
 * <h3>Example: Contract side</h3>
 * <p>A capability extending its required base contract:
 *
 * <pre>{@code
 * @PluginContract(role = PluginContract.Role.BASE)
 * public interface FooBar extends Plugin {
 *     int API_LEVEL = 1;
 *     String getMediaType();
 * }
 *
 * @PluginContract(
 *     role = PluginContract.Role.CAPABILITY,
 *     requires = { FooBar.class }
 * )
 * public interface BarBeque extends FooBar {
 *     int API_LEVEL = 1;
 *
 *     default String getMediaType() {
 *         return "application/bbq";
 *     }
 * }
 * }</pre>
 */
package io.gdcc.spi.meta.annotations;
