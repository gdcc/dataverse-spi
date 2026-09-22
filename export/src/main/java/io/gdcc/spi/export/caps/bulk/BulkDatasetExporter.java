package io.gdcc.spi.export.caps.bulk;

import io.gdcc.spi.export.ExportException;
import io.gdcc.spi.export.Exporter;
import io.gdcc.spi.meta.annotations.PluginContract;
import io.gdcc.spi.meta.annotations.RequiredProvider;
import io.gdcc.spi.meta.plugin.Plugin;

import java.io.OutputStream;

/**
 * Optional {@link Exporter} capability, combining exports of several dataset versions into a single document.
 * <p>
 * Combining is inherently format-specific. The platform cannot know any of that, so it resolves and supplies
 * the individual exports and leaves all framing to the exporter.
 * <p>
 * Because this capability sits on the same plugin as the {@link Exporter} it extends, the combined document is
 * expected to be of that exporter's own media type, built from that exporter's own single-dataset exports.
 * <p>
 * A minimal JSON implementation:
 * <pre>{@code
 * @Override
 * public void exportBulk(BulkDatasetContext context, OutputStream output) throws ExportException {
 *     try {
 *         output.write('[');
 *         boolean first = true;
 *         for (BulkDatasetContext.Item item : context.items()) {
 *             if (!first) {
 *                 output.write(',');
 *             }
 *             first = false;
 *             item.writeTo(output);
 *         }
 *         output.write(']');
 *     } catch (IOException ex) {
 *         throw new ExportException("Bulk export failed", ex);
 *     }
 * }
 * }</pre>
 *
 * @see BulkDatasetContext
 */
@PluginContract(
    role = PluginContract.Role.CAPABILITY,
    requires = Exporter.class,
    providers = @RequiredProvider(BulkDatasetContext.class)
)
public interface BulkDatasetExporter extends Plugin {
    
    int API_LEVEL = 1;
    
    /**
     * Write the combined export of the batch of dataset versions described by {@code context} to {@code output}.
     * <p>
     * Implementations own the framing entirely. Anything written around and between the individual exports is up
     * to them, including whatever is needed to keep the document valid when an item fails.
     * Remember: great care needs to be taken about resource usage and thread safety. Do not naively spawn new
     * threads or processes from within this method.
     * <p>
     * Note that the response status and headers are already committed by the time this runs.
     * A failure here cannot be turned into an error response, it truncates the body instead.
     * Prefer degrading gracefully over aborting where the format allows it.
     *
     * @implSpec <ul>
     *              <li><b>Do not close {@code output}.</b>
     *                  It belongs to the platform, which writes the response around this call.
     *                  It is close-shielded, so an accidental close is ignored rather than fatal, but flushing is fine
     *                  (and a good idea between items on large batches).</li>
     *              <li><b>Do not retain {@code context}, its items, or any stream beyond the duration of this call.</b>
     *                  They are bound to one request and become invalid once it returns.</li>
     *              <li><b>Handle per-item failures deliberately.</b>
     *                  An item whose export cannot be produced throws {@link java.io.IOException} from
     *                  {@link BulkDatasetContext.Item#writeTo(OutputStream)} or {@link BulkDatasetContext.Item#open()},
     *                  and does so before any of that item's bytes are written.
     *                  An implementation may therefore skip the item, substitute an error placeholder, or abort.
     *                  All three leave the document in a state it chose.
     *                  (Letting the exception escape as an {@link ExportException} aborts the response.)</li>
     *           </ul>
     *
     * @param context the batch to export; never null and never empty
     * @param output the stream receiving the combined document; never null, must not be closed by the implementation
     * @throws ExportException if the combined document cannot be produced
     */
    void exportBulk(BulkDatasetContext context, OutputStream output) throws ExportException;

}