package io.gdcc.spi.export;

import io.gdcc.spi.meta.annotations.PluginContract;
import io.gdcc.spi.meta.annotations.RequiredProvider;
import io.gdcc.spi.meta.plugin.Plugin;

import java.io.OutputStream;
import java.util.List;

@PluginContract(
    role = PluginContract.Role.CAPABILITY,
    requires = Exporter.class,
    providers = @RequiredProvider(ExportDataProvider.class)
)
public interface MultiDatasetExporter extends Plugin {
    
    int API_LEVEL = 1;
    
    /**
     * Exports multiple datasets provided by the given list of data providers to the specified output stream.
     *
     * @param datasetProviders a list of data providers, each representing a dataset to be exported
     * @param outputStream the output stream where the exported datasets will be written
     * @throws ExportException if an error occurs during the export process
     */
    void exportMultiple(List<ExportDataProvider> datasetProviders, OutputStream outputStream) throws ExportException;

}
