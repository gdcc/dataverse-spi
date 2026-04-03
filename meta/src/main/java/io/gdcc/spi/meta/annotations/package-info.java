/**
 * Annotations used to declare Dataverse plugin contracts, plugin implementations,
 * and required core providers.
 *
 * <p>This package defines the author-facing SPI model:</p>
 * <ul>
 *   <li>a {@linkplain io.gdcc.spi.meta.annotations.PluginContract.Role#BASE base contract}
 *       is the unique, directly loadable identity of a plugin,</li>
 *   <li>a {@linkplain io.gdcc.spi.meta.annotations.PluginContract.Role#CAPABILITY capability contract}
 *       adds optional functionality but is never loaded directly,</li>
 *   <li>a {@linkplain io.gdcc.spi.meta.annotations.DataversePlugin plugin implementation}
 *       must implement exactly one base contract and may additionally implement compatible capabilities,</li>
 *   <li>a {@linkplain io.gdcc.spi.meta.annotations.RequiredProvider required provider}
 *       declares Dataverse infrastructure contracts needed by a plugin contract.</li>
 * </ul>
 *
 * <p>Only base contracts are used as plugin loading identities.</p>
 *
 * <p>Contract interfaces must extend {@link io.gdcc.spi.meta.plugin.Plugin}, declare
 * {@link io.gdcc.spi.meta.annotations.PluginContract}, and provide a compile-time
 * {@code int API_LEVEL} constant. Plugin contracts must not extend other plugin contracts
 * (with the single exception of a capability extending a required base contract).</p>
 *
 * <p>Capabilities are attached to a plugin through normal Java interface implementation.
 * This allows SPI authors to provide additional methods and default implementations
 * without introducing ambiguity into plugin loading. If multiple implemented interfaces
 * contribute conflicting default methods, the plugin implementation class must resolve
 * that conflict explicitly.</p>
 *
 * <p>Example with extending base contract:</p>
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
 *
 * @DataversePlugin
 * public class Grill implements FooBar, BarBeque {
 *     // no override needed unless another default conflicts
 * }
 * }</pre>
 */
package io.gdcc.spi.meta.annotations;
