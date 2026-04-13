package io.gdcc.spi.export;

import io.gdcc.spi.meta.annotations.PluginContract;
import io.gdcc.spi.meta.annotations.RequiredProvider;
import io.gdcc.spi.meta.plugin.Plugin;

import java.io.OutputStream;

@PluginContract(
    role = PluginContract.Role.CAPABILITY,
    requires = Exporter.class,
    providers = @RequiredProvider(ExportFileProvider.class)
)
public interface FileExporter extends Plugin {
    
    int API_LEVEL = 1;
    
    /**
     * Exports a file's metadata using the metadata provider and writes the output to the specified output stream.
     *
     * @param fileDataProvider the provider containing the file-related data and metadata required for export
     * @param outputStream the output stream where the exported file data will be written
     */
    void exportFile(ExportFileProvider fileDataProvider, OutputStream outputStream);
    
}
