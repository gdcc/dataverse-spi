package io.gdcc.spi.export.caps.bulk;

import io.gdcc.spi.export.ExportException;
import io.gdcc.spi.export.Exporter;
import io.gdcc.spi.meta.annotations.PluginContract;
import io.gdcc.spi.meta.annotations.RequiredProvider;
import io.gdcc.spi.meta.plugin.Plugin;

import java.io.OutputStream;

@PluginContract(
    role = PluginContract.Role.CAPABILITY,
    requires = Exporter.class,
    providers = @RequiredProvider(BulkDatasetContext.class)
)
public interface BulkDatasetExporter extends Plugin {
    
    int API_LEVEL = 1;
    
    /**
     * Exports datasets in bulk to the provided output stream.
     * <p>
     * This method processes the items contained within the {@code BulkDatasetContext} and writes their content
     * plus any additional wrapping to the specified {@code OutputStream}.
     * <p>
     * If necessary, content may be manipulated, but great care needs to be taken about resource usage and thread safety.
     * Do not naively spawn new threads or processes from within this method.
     * <p>
     * The output stream is not closed by this method and must be managed externally.
     *
     * @param context the context providing access to the bulk dataset to be exported
     * @param output the stream to which the dataset will be written
     * @throws ExportException if an error occurs during the export process, including data inconsistency or I/O issues
     * @implNote Items must be consumed in iteration order and at most once each.
     *           They resolve lazily and read blocks until its export is available.
     *           The {@code output} stream must not be closed by the plugin.
     */
    void exportBulk(BulkDatasetContext context, OutputStream output) throws ExportException;

}
