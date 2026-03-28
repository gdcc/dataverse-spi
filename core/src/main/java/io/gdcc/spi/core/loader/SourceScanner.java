package io.gdcc.spi.core.loader;

import io.gdcc.spi.meta.descriptor.SourcedDescriptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Functional interface for scanning a specified path on the filesystem
 * to discover and retrieve plugin descriptors.
 *
 * Implementations of this interface are responsible for performing the
 * scanning operation on the provided {@link Path} and returning a list
 * of descriptors that represent the discovered plugins.
 *
 * For now, this is mostly used to allow injecting custom scanners for
 * testing purposes. As such, it is kept package-private.
 *
 * @see io.gdcc.spi.meta.descriptor.DescriptorScanner
 */
@FunctionalInterface
interface SourceScanner {
    List<SourcedDescriptor> scanPath(Path source) throws IOException;
}
