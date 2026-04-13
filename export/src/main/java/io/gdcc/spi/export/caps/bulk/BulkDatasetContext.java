package io.gdcc.spi.export.caps.bulk;

import io.gdcc.spi.meta.plugin.CoreProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides a context for managing and exporting a collection of exportable items as a bulk dataset.
 * <p>
 * The BulkDatasetContext serves as an abstraction for accessing a dataset's items.
 * Use the provided mechanisms for individual item-level operations such as streaming or writing data to an output stream.
 * <p>
 * This interface extends {@code CoreProvider}, thus metadata about the API version in use by a plugin will be recorded
 * in plugin metadata, validatable at load time.
 * <p>
 * As a core provider, plugins are consumers, not implementors of this interface.
 * @apiNote On breaking changes, remember to increment the {@link API_LEVEL} constant!
 */
public interface BulkDatasetContext extends CoreProvider {
    
    int API_LEVEL = 1;
    
    int size();
    Iterable<Item> items();
    
    /**
     * Represents an individual exportable item within a bulk dataset context.
     * An {@code Item} provides unique identification, versioning information, and mechanisms for export operations.
     * <p>
     * @apiNote On breaking changes, remember to increment the {@link API_LEVEL} constant!
     */
    interface Item {
        String persistentId();
        String versionNumber();
        
        /**
         * Writes the content of this item to the specified {@code OutputStream}.
         * The output stream is not closed by this method and must be managed externally.
         * <p>
         * Blocks until this item's export is available, then writes it. Fast path: no parsing, no copy.
         *
         * @param output the {@code OutputStream} to which the item's data will be written
         * @return the number of bytes written to the {@code OutputStream}
         * @throws IOException if an I/O error occurs during the write process
         */
        long writeTo(OutputStream output) throws IOException;
        
        /** For plugins that must parse or transform. The platform closes the stream; do not retain it. */
        InputStream open() throws IOException;
    }
}
