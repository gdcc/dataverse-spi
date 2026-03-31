package io.gdcc.spi.meta.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a concrete plugin implementation class for metadata generation.
 *
 * <p>Plugin authors should place this annotation on every concrete plugin implementation
 * class that is meant to be discovered and loaded by Dataverse.</p>
 *
 * <p>Annotated classes are validated by the {@link io.gdcc.spi.meta.processor.PluginContractProcessor annotation processor}
 * and contribute generated compatibility metadata used during plugin loading.</p>
 *
 * <p>Implementation rules:</p>
 * <ul>
 *   <li>the annotated type must be a {@code public}, non-abstract class,</li>
 *   <li>it must implement exactly one {@link PluginContract.Role#BASE base contract},</li>
 *   <li>it may additionally implement any number of {@link PluginContract.Role#CAPABILITY capability contracts}.</li>
 * </ul>
 *
 * <p>A capability contract is never loadable on its own. A plugin implementing a capability
 * must also implement the capability's required base contract. The base contract is the single hook
 * the Dataverse core uses to discover and load your plugin.</p>
 *
 * @implNote Example where {@code Exporter} is a base contract and {@code FooExporter} a capability:
 * <pre>{@code
 * @DataversePlugin
 * public class MyBarExporter implements Exporter, FooExporter {
 *     // Your implementation goes here...
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface DataversePlugin {
}
