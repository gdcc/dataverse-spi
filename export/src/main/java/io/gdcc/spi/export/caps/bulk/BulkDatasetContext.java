package io.gdcc.spi.export.caps.bulk;

import io.gdcc.spi.meta.plugin.CoreProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides a context for managing and exporting a collection of dataset versions in bulk.
 * <p>
 * The BulkDatasetContext serves as an abstraction for accessing a dataset's items.
 * Use the provided mechanisms for individual item-level operations such as streaming or writing data to an output stream.
 * <p>
 * This interface extends {@code CoreProvider}, thus metadata about the API version in use by a plugin will be recorded
 * in plugin metadata, validatable at load time.
 * As a core provider, plugins are consumers, not implementors of this interface.
 *
 * @apiNote On breaking changes, remember to increment the {@link API_LEVEL} constant!
 */
public interface BulkDatasetContext extends CoreProvider {
    
    int API_LEVEL = 1;
    
    /**
     * The number of dataset versions in this batch. Always greater than zero.
     */
    int size();
    
    /**
     * The batch, in the order requested by the API caller.
     * <p>
     * Re-iterable: calling this more than once, or iterating the result more than once, is explicitly allowed and
     * yields the same items in the same order.
     */
    Iterable<Item> items();
    
    /**
     * Represents an individual exportable item within a bulk dataset context.
     * An {@code Item} provides unique identification, versioning information, and mechanisms for export operations.
     * <p>
     * Labels are resolved up front and cost nothing to read.
     * <p>
     * Export data, by contrast, is produced on demand: each call to {@link #writeTo(OutputStream)} or {@link #open()}
     * resolves the export anew, which for a published version means a storage read and for a draft means generating
     * the export from scratch.
     * <p>
     * Re-reading is permitted but not free, so an implementation needing the same bytes twice is usually better off
     * buffering them itself, where it can weigh the cost against the size of the data.
     * <p>
     * Whichever method is used, the export is fully produced before any byte is handed over.
     * A failure therefore surfaces before the item has contributed anything to the output,
     * which is what makes graceful per-item degradation possible.
     *
     * @apiNote On breaking changes, remember to increment the {@link API_LEVEL} constant!
     */
    interface Item {
        
        /**
         * The persistent identifier of the dataset this version belongs to, e.g. {@code doi:10.5072/FK2/ABC123}.
         * Suitable for manifests, envelopes, entry names and error messages.
         */
        String persistentId();
        
        /**
         * The friendly version number of this dataset version, e.g. {@code 1.0} or {@code DRAFT}.
         */
        String versionNumber();
        
        /**
         * Writes the content of this item to the specified {@code OutputStream}.
         * Blocks until this item's export is available, then writes it. Fast path: no parsing, no copy.
         * <p>
         * This is the preferred way to emit an item unchanged, as-is:
         * It leaks nothing even if the copy fails, and it avoids handling the stream at all.
         * Nothing is written to {@code output} unless the export was produced successfully.
         * <p>
         *
         * <p>
         *
         *
         * @param output the {@code OutputStream} to which the item's data will be written
         * @return the number of bytes written to the {@code OutputStream}
         * @throws IOException if an I/O error occurs during the write process
         * @throws IllegalArgumentException if the given output stream is null
         */
        long writeTo(OutputStream output) throws IOException;
        
        /**
         * Resolves this item's export and returns it as a stream.
         * For implementations that need to parse or transform the data rather than pass it through.
         * <p>
         * Callers should close the returned stream, ideally with try-with-resources, since each open stream holds
         * an underlying storage resource. Forgetting to is not fatal: every stream opened through this context is
         * tracked and released when the export finishes. Closing more than once is harmless.
         * <p>
         * The returned stream is not thread-safe and must not be shared between threads.
         *
         * @return an open stream over this item's export (never null)
         * @throws IOException if the export cannot be produced
         */
        InputStream open() throws IOException;
    }
}
