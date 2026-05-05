/**
 * Provides annotation-processing support for generation of Dataverse Plugin Metadata.
 *
 * <p>This package contains compile-time infrastructure for validating plugin declarations and producing metadata
 * resources consumed by the runtime plugin loading mechanism.
 *
 * <h2>Overview</h2>
 *
 * <p>The processor in this package connects the declaration model in {@link io.gdcc.spi.meta.annotations},
 * the core SPI contracts in {@link io.gdcc.spi.meta.plugin}, and the descriptor model in {@link io.gdcc.spi.meta.descriptor}.
 *
 * <p>It evaluates
 * {@link io.gdcc.spi.meta.annotations.DataversePlugin @DataversePlugin},
 * {@link io.gdcc.spi.meta.annotations.PluginContract @PluginContract}, and
 * {@link io.gdcc.spi.meta.annotations.RequiredProvider @RequiredProvider}
 * declarations on types derived from {@link io.gdcc.spi.meta.plugin.Plugin} and {@link io.gdcc.spi.meta.plugin.CoreProvider},
 * validates their structure, and generates serialized {@link io.gdcc.spi.meta.descriptor.Descriptor descriptor}
 * resources in the format defined by {@link io.gdcc.spi.meta.descriptor.DescriptorFormat}.
 *
 * <h2>Generated metadata</h2>
 *
 * <p>The primary generated output is descriptor metadata under {@value io.gdcc.spi.meta.descriptor.DescriptorFormat#DESCRIPTOR_DIRECTORY}.
 * These descriptors are intended to serve as the authoritative source for plugin discovery and compatibility metadata,
 * and may later be discovered by {@link io.gdcc.spi.meta.descriptor.DescriptorScanner} and resolved into runtime-facing
 * models such as {@link io.gdcc.spi.meta.descriptor.PluginDescriptor} by the plugin loader.
 *
 * <p>For compatibility with older runtime environments, the processor may also emit {@code META-INF/services} resources.
 * That mechanism is transitional: descriptor-based discovery is the preferred direction, as it avoids several class-loading
 * and isolation limitations associated with {@link java.util.ServiceLoader ServiceLoader}-based loading alone.
 *
 * <h2>Maven configuration</h2>
 *
 * <p>On JDK 22 and newer, annotation processors should be configured explicitly in the build.
 * In Maven, this is typically done through the {@code maven-compiler-plugin}, for example:
 *
 * <pre>{@code
 * <build>
 *   <plugins>
 *     <plugin>
 *       <artifactId>maven-compiler-plugin</artifactId>
 *       <configuration>
 *         <annotationProcessorPaths>
 *           <path>
 *             <groupId>io.gdcc</groupId>
 *             <artifactId>dataverse-spi</artifactId>
 *             <version>${spi.version}</version>
 *           </path>
 *         </annotationProcessorPaths>
 *       </configuration>
 *     </plugin>
 *   </plugins>
 * </build>
 * }</pre>
 *
 * <p>Types in this package are mainly internal build-time infrastructure rather than part of the public runtime API.
 */
package io.gdcc.spi.meta.processor;